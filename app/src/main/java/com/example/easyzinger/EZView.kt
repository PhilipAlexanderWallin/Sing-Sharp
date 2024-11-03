package com.example.easyzinger

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class EZView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var text: String = "A" // Default text
    private var textSize: Float = 100f // Default text size
    private var textColor: Int = Color.WHITE // Default text color

    val paint = Paint().apply {
        color = textColor
        textSize = this@EZView.textSize
        textAlign = Paint.Align.CENTER
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Calculate text position
        val x = width / 2f
        val y = (height / 2f - (paint.descent() + paint.ascent()) / 2)

        // Draw the text
        canvas.drawText(text, x, y, paint)

        // Draw the line underneath the text
        val lineY = y + (paint.descent() - paint.ascent()) / 2 + 10 // Adjust position as needed
        canvas.drawLine(0f, lineY, width.toFloat(), lineY, paint.apply { strokeWidth = 5f }) // Line thickness
    }
}
