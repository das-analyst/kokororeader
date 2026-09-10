package com.kokoro.tts.reader.ui

import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.kokoro.tts.R
import com.kokoro.tts.reader.model.ReaderTheme
import com.kokoro.tts.reader.model.SentenceItem

class SentenceAdapter(
    private var sentences: List<SentenceItem> = emptyList(),
    private var currentTheme: ReaderTheme = ReaderTheme.CLEAN_PAPER,
    private var textSizeSp: Float = 18f,
    private var lineSpacingMultiplier: Float = 1.45f,
    private val onSentenceClicked: (Int) -> Unit
) : RecyclerView.Adapter<SentenceAdapter.SentenceViewHolder>() {

    private var activeIndex: Int = -1

    fun updateSentences(newSentences: List<SentenceItem>) {
        sentences = newSentences
        activeIndex = -1
        notifyDataSetChanged()
    }

    fun setActiveIndex(index: Int) {
        val oldIndex = activeIndex
        activeIndex = index
        if (oldIndex in sentences.indices) {
            notifyItemChanged(oldIndex)
        }
        if (activeIndex in sentences.indices) {
            notifyItemChanged(activeIndex)
        }
    }

    fun setTheme(theme: ReaderTheme) {
        currentTheme = theme
        notifyDataSetChanged()
    }

    fun setTextSize(sizeSp: Float) {
        textSizeSp = sizeSp
        notifyDataSetChanged()
    }

    fun setLineSpacing(multiplier: Float) {
        lineSpacingMultiplier = multiplier
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SentenceViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sentence, parent, false)
        return SentenceViewHolder(view)
    }

    override fun onBindViewHolder(holder: SentenceViewHolder, position: Int) {
        val item = sentences[position]
        val isActive = position == activeIndex

        holder.tvText.text = item.text
        holder.tvText.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
        holder.tvText.setLineSpacing(0f, lineSpacingMultiplier)

        // Paragraph separation spacing
        val density = holder.itemView.resources.displayMetrics.density
        val topPadding = (4 * density).toInt()
        val bottomPadding = if (item.isParagraphEnd) (16 * density).toInt() else (4 * density).toInt()
        val sidePadding = (16 * density).toInt()
        holder.container.setPadding(sidePadding, topPadding, sidePadding, bottomPadding)

        if (isActive) {
            holder.container.setBackgroundColor(currentTheme.activeHighlightColor)
            holder.tvText.setTextColor(currentTheme.activeTextColor)
            holder.tvText.setTypeface(null, Typeface.BOLD)
        } else {
            holder.container.setBackgroundColor(Color.TRANSPARENT)
            holder.tvText.setTextColor(currentTheme.primaryTextColor)
            holder.tvText.setTypeface(null, Typeface.NORMAL)
        }

        holder.container.setOnClickListener {
            onSentenceClicked(position)
        }
    }

    override fun getItemCount(): Int = sentences.size

    class SentenceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val container: View = itemView.findViewById(R.id.sentenceContainer)
        val tvText: TextView = itemView.findViewById(R.id.tvSentenceText)
    }
}
