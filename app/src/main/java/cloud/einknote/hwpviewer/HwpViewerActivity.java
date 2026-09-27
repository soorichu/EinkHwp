package cloud.einknote.hwpviewer;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.KeyEvent;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HwpViewerActivity extends AppCompatActivity {

    private WebView webView;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private int currentTextZoom = 100;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hwp_viewer);

        webView = findViewById(R.id.webView);
        Button btnBack = findViewById(R.id.btnBack);
        Button btnPrev = findViewById(R.id.btnPrev);
        Button btnNext = findViewById(R.id.btnNext);
        Button btnFontDecrease = findViewById(R.id.btnFontDecrease);
        Button btnFontIncrease = findViewById(R.id.btnFontIncrease);
        TextView tvViewerTitle = findViewById(R.id.tvViewerTitle);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setDisplayZoomControls(false);
        settings.setBuiltInZoomControls(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setTextZoom(currentTextZoom);

        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setBackgroundColor(0xFFFFFFFF);

        String filePath = getIntent().getStringExtra("FILE_PATH");
        String fontPath = getIntent().getStringExtra("FONT_PATH");

        if (filePath == null || filePath.trim().isEmpty()) {
            Toast.makeText(this, "파일 경로가 없습니다.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        File file = new File(filePath);
        tvViewerTitle.setText(file.getName());

        executor.execute(() -> {
            try {
                String html = HwpConverter.toEinkHtml(file, fontPath);
                runOnUiThread(() -> webView.loadDataWithBaseURL("file:///", html, "text/html", "UTF-8", null));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(HwpViewerActivity.this, "파일 열기 실패: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });

        // 좌측 상단 뒤로가기 버튼
        btnBack.setOnClickListener(v -> finish());

        // 스크롤 및 글자 크기 조절
        btnPrev.setOnClickListener(v -> scrollVertical(false));
        btnNext.setOnClickListener(v -> scrollVertical(true));
        btnFontDecrease.setOnClickListener(v -> adjustFontSize(-10));
        btnFontIncrease.setOnClickListener(v -> adjustFontSize(10));
    }

    private void adjustFontSize(int delta) {
        currentTextZoom += delta;
        if (currentTextZoom < 50) currentTextZoom = 50;
        if (currentTextZoom > 250) currentTextZoom = 250;
        webView.getSettings().setTextZoom(currentTextZoom);
    }

    private void scrollVertical(boolean down) {
        String sign = down ? "" : "-";
        String js = "window.scrollBy({ top: " + sign + "(window.innerHeight * 0.88), behavior: 'instant' });";
        webView.evaluateJavascript(js, null);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            scrollVertical(true);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            scrollVertical(false);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}