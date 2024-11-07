package com.example.andro;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class ViewActivity extends AppCompatActivity {
    private WebView webView;
    private Button button2;
    private Handler handler = new Handler(); // Handler to post delayed tasks

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.view_new); // Use the layout XML file

        // Initialize WebView and Button
        webView = findViewById(R.id.webView);
        button2 = findViewById(R.id.button2);

        // Set up WebView
        webView.setWebViewClient(new WebViewClient()); // This ensures links are opened in the WebView itself
        webView.getSettings().setJavaScriptEnabled(true); // Enable JavaScript if needed
        webView.loadUrl("http://srfoodtruck.ddns.net:8034/"); // Replace with the URL you want to load

        // Make the button invisible initially
        button2.setVisibility(View.INVISIBLE);

        // Set up Button click listener
        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Create an intent to start MainActivity
                Intent intent = new Intent(ViewActivity.this, MainActivity.class);
                startActivity(intent);
            }
        });

        // Make the button visible after a delay of 5 seconds (5000 milliseconds)
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                button2.setVisibility(View.VISIBLE);
            }
        }, 15000); // 5000 milliseconds = 5 seconds
    }
}
