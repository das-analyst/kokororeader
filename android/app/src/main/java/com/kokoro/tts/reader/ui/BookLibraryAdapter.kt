package com.kokoro.tts.reader.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.kokoro.tts.R
import com.kokoro.tts.reader.model.SavedBook

class BookLibraryAdapter(
    private val onReadClicked: (SavedBook) -> Unit,
    private val onExportClicked: (SavedBook) -> Unit,
    private val onDeleteClicked: (SavedBook) -> Unit
) : RecyclerView.Adapter<BookLibraryAdapter.BookViewHolder>() {

    private val items = mutableListOf<SavedBook>()

    fun submitList(newItems: List<SavedBook>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_saved_book, parent, false)
        return BookViewHolder(view)
    }

    override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class BookViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvFormatBadge: TextView = itemView.findViewById(R.id.tvFormatBadge)
        private val tvBookTitle: TextView = itemView.findViewById(R.id.tvBookTitle)
        private val tvBookDetails: TextView = itemView.findViewById(R.id.tvBookDetails)
        private val btnExportBook: ImageButton = itemView.findViewById(R.id.btnExportBook)
        private val btnDeleteBook: ImageButton = itemView.findViewById(R.id.btnDeleteBook)
        private val btnReadBook: MaterialButton = itemView.findViewById(R.id.btnReadBook)

        fun bind(book: SavedBook) {
            tvBookTitle.text = book.title
            tvFormatBadge.text = book.originalFormat

            when (book.originalFormat.uppercase()) {
                "PDF" -> tvFormatBadge.setBackgroundColor(Color.parseColor("#C62828")) // Red
                "EPUB" -> tvFormatBadge.setBackgroundColor(Color.parseColor("#2E7D32")) // Green
                else -> tvFormatBadge.setBackgroundColor(Color.parseColor("#1565C0")) // Blue
            }

            val sizeKb = book.fileSizeBytes / 1024
            val sizeStr = if (sizeKb > 1024) String.format("%.1f MB", sizeKb / 1024.0) else "$sizeKb KB"
            tvBookDetails.text = "${book.chapterCount} Chapters • $sizeStr"

            btnReadBook.setOnClickListener { onReadClicked(book) }
            btnExportBook.setOnClickListener { onExportClicked(book) }
            btnDeleteBook.setOnClickListener { onDeleteClicked(book) }
        }
    }
}
