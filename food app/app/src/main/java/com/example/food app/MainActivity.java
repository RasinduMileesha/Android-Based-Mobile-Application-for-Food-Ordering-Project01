package com.example.andro;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Set;
import java.util.UUID;

import de.codecrafters.tableview.TableView;
import de.codecrafters.tableview.toolkit.SimpleTableDataAdapter;
import de.codecrafters.tableview.toolkit.SimpleTableHeaderAdapter;

public class MainActivity extends AppCompatActivity {

    private Connection connect;
    private String connectionResult = "";
    private TableView<String[]> tableView;
    private Button loadDataButton;
    private Button pdfButton;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;
    private TextView[] textViews;
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String PRINTER_NAME = "watch 8"; // Replace with your printer's name

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tableView = findViewById(R.id.tableView);
        String[] headers = {"Description", "Quantity", "Total"};
        tableView.setHeaderAdapter(new SimpleTableHeaderAdapter(this, headers));

        pdfButton = findViewById(R.id.printBill);
        loadDataButton = findViewById(R.id.button);

        textViews = new TextView[]{
                findViewById(R.id.SubTotaltextView),
                findViewById(R.id.DiscounttextView),
                findViewById(R.id.GrandTotaltextView),
                findViewById(R.id.CashtextView),
                findViewById(R.id.CardtextView)
        };

        Typeface monoTypeface = Typeface.create("monospace", Typeface.NORMAL);
        for (TextView textView : textViews) {
            textView.setTypeface(monoTypeface);
        }

        pdfButton.setOnClickListener(view -> {
            if (checkPermission()) {
                try {
                    createPDFAndPrint(textViews);
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e("BluetoothPrint", "Exception: " + e.getMessage());
                    Toast.makeText(MainActivity.this, "Error printing or creating PDF", Toast.LENGTH_LONG).show();
                }
            } else {
                requestPermission();
            }
        });

        loadDataButton.setOnClickListener(view -> getDataForTableView());
    }

    public void getDataForTableView() {
        try {
            ConnectionHelper connectionHelper = new ConnectionHelper();
            connect = connectionHelper.conclass();
            if (connect != null) {
                String query = "SELECT cast(STT.PSaleTransID as varchar(max)),\n" +
                        "isnull((Select PItemName From PastryItemTable Where PItemID = STT.FK_PItemID),'') AS ItemName,\n" +
                        "cast(STT.RetailPrice as varchar(max))  AS SellingPrice, \n" +
                        "cast(STT.SellingQuantity as varchar(max)) AS Quantity,\n" +
                        "cast((select sum(SubTotal) from PastrySaleTransactionTable where FK_PSaleID = STT.FK_PSaleID) as varchar(max)) AS SubTotal,\n" +
                        "cast((select sum(Discount) from PastrySaleTransactionTable where FK_PSaleID = STT.FK_PSaleID) as varchar(max)) AS Discount, \n" +
                        "cast((select sum(TotalWithTax) from PastrySaleTransactionTable where FK_PSaleID = STT.FK_PSaleID) as varchar(max)) as TotalX,\n" +
                        "cast((Select isNull(Sum(PayingAmount),0) From PastrySalePaymentTable Where PaymentType = 'Cash' AND FK_PSaleID = STT.FK_PSaleID)as varchar(max)) AS Cash,\n" +
                        "cast((Select isNull(Sum(PayingAmount),0) From PastrySalePaymentTable Where PaymentType = 'Card' AND FK_PSaleID = STT.FK_PSaleID)as varchar(max)) AS Card\n" +
                        "\n" +
                        "FROM PastrySaleTransactionTable AS STT\n" +
                        "WHERE STT.FK_PSaleID = (select top 1 PSaleID from PastrySaleTable order by PSaleID desc)\n" +
                        "order by STT.PSaleTransID ";
                Statement smt = connect.createStatement();
                ResultSet rs = smt.executeQuery(query);

                String[][] data = new String[10][3];
                int index = 0;
                boolean firstRecord = true;

                while (rs.next()) {
                    if (index < 10) {
                        data[index][0] = rs.getString("ItemName");
                        data[index][1] = rs.getString("Quantity");
                        data[index][2] = rs.getString("SellingPrice");
                        index++;
                    }

                    if (firstRecord) {
                        textViews[0].setText(rs.getString("SubTotal"));
                        textViews[1].setText(rs.getString("Discount"));
                        textViews[2].setText(rs.getString("TotalX"));
                        textViews[3].setText(rs.getString("Cash"));
                        textViews[4].setText(rs.getString("Card"));
                        firstRecord = false;
                    }
                }

                tableView.setDataAdapter(new SimpleTableDataAdapter(this, data));

            } else {
                connectionResult = "Check Connection";
                Toast.makeText(this, connectionResult, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception ex) {
            Log.e("DataRetrieval", "Error: " + ex.getMessage());
            Toast.makeText(this, "Error retrieving data", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            int result = ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            return result == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                intent.addCategory("android.intent.category.DEFAULT");
                intent.setData(Uri.parse(String.format("package:%s", getApplicationContext().getPackageName())));
                startActivityForResult(intent, PERMISSION_REQUEST_CODE);
            } catch (Exception e) {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivityForResult(intent, PERMISSION_REQUEST_CODE);
            }
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    Toast.makeText(this, "Permission Granted", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "Permission Denied", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission Granted", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Permission Denied", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void createPDFAndPrint(TextView[] textViews) {
        // Generate the bill content for PDF and Bluetooth printing
        StringBuilder printData = new StringBuilder();
        printData.append("      Seethawaka Regency         \n");
        printData.append("         Avissawella         \n");
        printData.append("295 Avissawella Rd, Avissawella\n");
        printData.append("  0753 222711 | 0753 222720\n");
        printData.append("reservations@seethawakaregency.com\n");
        printData.append("\n");
        printData.append("--------------------------------\n");
        printData.append("Description      Qty       Total\n");
        printData.append("--------------------------------\n");

        // Fetch data for each item from the database and add it to the printout
        try {
            ConnectionHelper connectionHelper = new ConnectionHelper();
            Connection connect = connectionHelper.conclass();
            if (connect != null) {
                String query = "SELECT cast(STT.PSaleTransID as varchar(max)),\n" +
                        "isnull((Select PItemName From PastryItemTable Where PItemID = STT.FK_PItemID),'') AS ItemName,\n" +
                        "cast(STT.RetailPrice as varchar(max))  AS SellingPrice, \n" +
                        "cast(STT.SellingQuantity as varchar(max)) AS Quantity,\n" +
                        "cast((select sum(SubTotal) from PastrySaleTransactionTable where FK_PSaleID = STT.FK_PSaleID) as varchar(max)) AS SubTotal,\n" +
                        "cast((select sum(Discount) from PastrySaleTransactionTable where FK_PSaleID = STT.FK_PSaleID) as varchar(max)) AS Discount, \n" +
                        "cast((select sum(TotalWithTax) from PastrySaleTransactionTable where FK_PSaleID = STT.FK_PSaleID) as varchar(max)) as TotalX,\n" +
                        "cast((Select isNull(Sum(PayingAmount),0) From PastrySalePaymentTable Where PaymentType = 'Cash' AND FK_PSaleID = STT.FK_PSaleID)as varchar(max)) AS Cash,\n" +
                        "cast((Select isNull(Sum(PayingAmount),0) From PastrySalePaymentTable Where PaymentType = 'Card' AND FK_PSaleID = STT.FK_PSaleID)as varchar(max)) AS Card\n" +
                        "\n" +
                        "FROM PastrySaleTransactionTable AS STT\n" +
                        "WHERE STT.FK_PSaleID = (select top 1 PSaleID from PastrySaleTable order by PSaleID desc)\n" +
                        "order by STT.PSaleTransID ";
                Statement smt = connect.createStatement();
                ResultSet rs = smt.executeQuery(query);

                while (rs.next()) {
                    String itemName = rs.getString("ItemName");
                    String quantity = rs.getString("Quantity");
                    String total = rs.getString("SellingPrice");

                    // Truncate itemName if it's too long
                    if (itemName.length() > 15) {
                        itemName = itemName.substring(0, 12) + "...";
                    }

                    // Format the text to ensure it fits within the margins
                    printData.append(String.format("%-15s %5s %10s\n", itemName, quantity, total));
                }


                }
        } catch (Exception e) {
            Log.e("DataRetrieval", "Error: " + e.getMessage());
            Toast.makeText(this, "Error retrieving data", Toast.LENGTH_SHORT).show();
            return;
        }

        // Add the totals to the printout

        printData.append("--------------------------------\n");
        printData.append("\n");
        printData.append("Discount: ").append(textViews.length > 1 ? textViews[1].getText().toString() : "").append("\n");
        printData.append("Cash: ").append(textViews.length > 3 ? textViews[3].getText().toString() : "").append("\n");
        printData.append("Card: ").append(textViews.length > 4 ? textViews[4].getText().toString() : "").append("\n");
        printData.append("\n");
        printData.append("--------------------------------\n");
        printData.append("SubTotal: ").append(textViews.length > 0 ? textViews[0].getText().toString() : "").append("\n");
        printData.append("--------------------------------\n");


        // Create the PDF document
        try {
            File pdfFile = createPDF(printData.toString());
            if (pdfFile != null) {
                // Print via Bluetooth
                printBillViaBluetooth(printData.toString());
                Toast.makeText(this, "PDF Generated", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e("PDFCreation", "Error: " + e.getMessage());
            Toast.makeText(this, "Error creating PDF", Toast.LENGTH_SHORT).show();
        }
    }
    private void drawText(Canvas canvas, Paint paint, String text, int x, int y, int maxWidth, boolean isBold, float textSize, boolean isCentered) {
        Paint textPaint = new Paint(paint);
        textPaint.setTextSize(textSize);
        if (isBold) {
            textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        } else {
            textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        }

        // Split the text into lines that fit within maxWidth
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            if (textPaint.measureText(line + word) <= maxWidth) {
                line.append(word).append(" ");
            } else {
                if (isCentered) {
                    float textWidth = textPaint.measureText(line.toString().trim());
                    canvas.drawText(line.toString().trim(), (maxWidth - textWidth) / 2 + x, y, textPaint);
                } else {
                    canvas.drawText(line.toString().trim(), x, y, textPaint);
                }
                y += textPaint.getTextSize();
                line = new StringBuilder().append(word).append(" ");
            }
        }

        if (isCentered) {
            float textWidth = textPaint.measureText(line.toString().trim());
            canvas.drawText(line.toString().trim(), (maxWidth - textWidth) / 2 + x, y, textPaint);
        } else {
            canvas.drawText(line.toString().trim(), x, y, textPaint);
        }
    }


    private File createPDF(String printData) throws Exception {
        PdfDocument document = new PdfDocument();
        Paint paint = new Paint();
        // Set the width to 80mm (approximately 226 pixels) and height to an arbitrary length
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(226, 500, 1).create();
        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();

        paint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));
        paint.setTextSize(8);

        int y = 10;
        for (String line : printData.split("\n")) {
            canvas.drawText(line, 10, y, paint);
            y += 10;
        }
        // Bold and larger text for "SubTotal"
        Paint boldPaint = new Paint();
        boldPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        boldPaint.setTextSize(12); // Larger text size for "SubTotal"

        y += 15; // Adjust space after previous content
        drawText(canvas, boldPaint, "GrandTotal: " + (textViews.length > 0 ? textViews[0].getText().toString() : ""), 10, y, 200, true, 12, false);

        y += 15;
        paint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));
        paint.setTextSize(8); // Regular text size
        drawText(canvas, paint, "THANK YOU!! for Purchase.", 10, y, 200, false, 8, false);

        document.finishPage(page);

        File pdfDir = new File(Environment.getExternalStorageDirectory(), "PDFs");
        if (!pdfDir.exists()) {
            pdfDir.mkdir();
        }

        File pdfFile = new File(pdfDir, "bill.pdf");
        document.writeTo(new FileOutputStream(pdfFile));
        document.close();

        return pdfFile;
    }

    private void printBillViaBluetooth(String printData) {
        try {
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (bluetoothAdapter == null) {
                Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!bluetoothAdapter.isEnabled()) {
                Toast.makeText(this, "Please enable Bluetooth", Toast.LENGTH_SHORT).show();
                return;
            }

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, PERMISSION_REQUEST_CODE);
                return;
            }

            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
            if (pairedDevices.size() > 0) {
                for (BluetoothDevice device : pairedDevices) {
                    if (device.getName().equals(PRINTER_NAME)) {
                        bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
                        bluetoothSocket.connect();
                        outputStream = bluetoothSocket.getOutputStream();
                        break;
                    }
                }
            }

            if (outputStream != null) {
                outputStream.write(printData.getBytes());
                outputStream.close();
                bluetoothSocket.close();
            } else {
                Toast.makeText(this, "Printer not found", Toast.LENGTH_SHORT).show();
            }

        } catch (Exception e) {
            Log.e("BluetoothPrint", "Error: " + e.getMessage());
            Toast.makeText(this, "Error printing bill", Toast.LENGTH_LONG).show();
        }
    }
}