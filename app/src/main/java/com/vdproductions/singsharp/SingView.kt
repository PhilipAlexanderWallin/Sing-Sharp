package com.vdproductions.singsharp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class SingView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var note: Triple<Int, Int, Float>? = null
    private var textSize: Float = 100f
    private var textColor: Int = Color.GREEN
    private val noteNames = arrayOf("C", "C", "D", "D", "E", "F", "F", "G", "G", "A", "A", "B")
    private val noteSigns = arrayOf("", "#", "", "#", "", "", "#", "", "#", "", "#", "")

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
        strokeWidth = 10f
    }

    val subAndSuperScript = Paint().apply {
        color = textColor
        textSize = this@SingView.textSize / 2
        textAlign = Paint.Align.CENTER
    }

    val notesDistance = 200;
    val lineHeight = 100;

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (note == null) {
            return;
        }

        // Calculate text position
        val x = width / 2f
        val y = (height / 2f - (paint.descent() + paint.ascent()) / 2)

        note?.let { (noteIndex, octave, centsOffset) ->
            // Draw the text
            val previousNoteIndex = if (noteIndex == 0) 11 else noteIndex - 1
            val previousOctave = if (noteIndex == 0) octave - 1 else octave
            drawNote(canvas, previousNoteIndex, previousOctave, x - notesDistance, y)

            drawNote(canvas, noteIndex, octave, x, y)

            val nextNoteIndex = if (noteIndex == 11) 0 else noteIndex + 1
            val nextOctave = if (noteIndex == 11) octave + 1 else octave
            drawNote(canvas, nextNoteIndex, nextOctave, x + notesDistance, y)

            val noteOffset = notesDistance * centsOffset / 50f
            canvas.drawLine(x + noteOffset, y - lineHeight / 2, x + noteOffset, y + lineHeight / 2, paint)
        }
    }

    fun drawNote(canvas: Canvas, noteIndex: Int, octave: Int, x: Float, y: Float) {
        canvas.drawText(noteNames[noteIndex], x, y, paint)
        canvas.drawText(noteSigns[noteIndex], x + paint.textSize * 0.5f, y - paint.textSize * 0.4f, subAndSuperScript)
        canvas.drawText(octave.toString(), x + paint.textSize * 0.5f, y + paint.textSize * 0.2f, subAndSuperScript)
        canvas.drawLine(x, y, x, y + lineHeight / 2, paint)
    }
}
