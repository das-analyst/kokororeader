package com.kokoro.tts.reader.ui

import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.kokoro.tts.R
import com.kokoro.tts.reader.model.SentenceItem

class SentenceAdapter(
    private var sentences: List<SentenceItem> = emptyList(),
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SentenceViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sentence, parent, false)
        return SentenceViewHolder(view)
    }

    override fun onBindViewHolder(holder: SentenceViewHolder, position: Int) {
        val item = sentences[position]
        val isActive = position == activeIndex

        holder.tvText.text = item.text

        if (isActive) {
            // Warm highlight for currently spoken sentence
            holder.container.setBackgroundColor(Color.parseColor("#FFE8A3"))
            holder.tvText.setTextColor(Color.parseColor("#1A1A1A"))
            holder.tvText.setTypeface(null, Typeface.BOLD)
        } else {
            holder.container.setBackgroundColor(Color.TRANSPARENT)
            holder.tvText.setTextColor(Color.parseColor("#333333"))
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
