package com.vdproductions.singsharp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class SingView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var note: String = "-"
    private var textSize: Float = 100f
    private var textColor: Int = Color.GREEN

    fun setViewModel(viewModel: SingViewModel) {
        // Observe LiveData
        viewModel.note.observeForever { newNote ->
            note = newNote
            invalidate()
        }
    }

    val paint = Paint().apply {
        color = textColor
        textSize = this@SingView.textSize
        textAlign = Paint.Align.CENTER
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Calculate text position
        val x = width / 2f
        val y = (height / 2f - (paint.descent() + paint.ascent()) / 2)

        // Draw the text
        canvas.drawText(note, x, y, paint)
    }
}
