package com.epd.st25dv16kc

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<android.view.View>(R.id.cardBinary).setOnClickListener {
            startActivity(Intent(this, BinaryActivity::class.java))
        }

        findViewById<android.view.View>(R.id.cardGray).setOnClickListener {
            startActivity(Intent(this, GrayScaleActivity::class.java))
        }

        findViewById<android.view.View>(R.id.cardFourGray).setOnClickListener {
            startActivity(Intent(this, FourGrayActivity::class.java))
        }

        findViewById<android.view.View>(R.id.cardCartoon).setOnClickListener {
            startActivity(Intent(this, CartoonActivity::class.java))
        }

        findViewById<android.view.View>(R.id.cardQr).setOnClickListener {
            startActivity(Intent(this, QrCodeActivity::class.java))
        }

        findViewById<android.view.View>(R.id.cardNote).setOnClickListener {
            startActivity(Intent(this, NoteActivity::class.java))
        }
    }
}