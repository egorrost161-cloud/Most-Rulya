package com.lada.wheelbridge;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Главный экран приложения.
 * Показывает статус доступа к уведомлениям и кнопку «Открыть настройки».
 */
public class SettingsActivity extends Activity {

    private boolean asked = false;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setGravity(Gravity.CENTER);

        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        // Текстовое поле статуса
        status = new TextView(this);
        status.setTextColor(Color.BLACK);
        status.setTextSize(22f);
        root.addView(status,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));

        // Кнопка «Открыть настройки»
        Button button = new Button(this);
        button.setText(R.string.button_open_settings);
        button.setTextSize(20f);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AccessPrompt.openSettings(SettingsActivity.this, true);
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = pad;
        root.addView(button, lp);

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (AccessPrompt.isEnabled(this)) {
            // Доступ выдан — показываем статус и запускаем сервис
            status.setText(R.string.status_access_granted);

            Intent i = new Intent(this, WheelBridgeService.class);
            startService(i);
        } else {
            // Доступа нет — показываем инструкцию
            status.setText(R.string.status_access_missing);

            if (!asked) {
                asked = true;
                if (!AccessPrompt.openSettings(this, true)) {
                    status.setText(R.string.status_cannot_open);
                }
            }
        }
    }
}
