package com.example.endproject.adapters

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.endproject.R
import com.example.endproject.models.AttendanceRecord

// מתאם להצגת רשימת נוכחות של עובד (משמש באזור האישי של העובד)
class AttendanceAdapter(
    private val records: List<AttendanceRecord>,                     // רשימת ימי נוכחות
    private val totalListener: ((Int) -> Unit)? = null,             // מאזין לסה"כ דקות (אופציונלי)
    private val showComments: Boolean = false                       // האם להציג הערות
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_ITEM = 0     // טיפוס שורה רגילה
        private const val TYPE_TOTAL = 1    // טיפוס שורת סיכום
    }

    // קובע איזה טיפוס ViewHolder נדרש לכל שורה
    override fun getItemViewType(position: Int): Int {
        return if (position == records.size - 1 && records[position].date == "סה״כ לחודש") TYPE_TOTAL else TYPE_ITEM
    }

    // יצירת ViewHolder לפי הטיפוס
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_attendance, parent, false)
        return if (viewType == TYPE_TOTAL) TotalViewHolder(view) else AttendanceViewHolder(view)
    }

    // קישור הנתונים לשורות בתצוגה
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val record = records[position]
        if (holder is AttendanceViewHolder) {
            holder.dateTV.text = record.date
            holder.checkInTV.text = record.checkIn
            holder.checkOutTV.text = record.checkOut
            holder.totalHoursTV.text = record.totalHours

            // כפתור מיקום כניסה
            holder.checkInLocButton.setOnClickListener {
                val messageView = TextView(holder.itemView.context).apply {
                    text = record.checkInLocation.ifBlank { "לא זמין" }
                    setTextColor(Color.BLACK)
                    textSize = 16f
                    setPadding(40, 40, 40, 40)
                }
                AlertDialog.Builder(holder.itemView.context)
                    .setTitle("מיקום כניסה")
                    .setView(messageView)
                    .setPositiveButton("סגור", null)
                    .show()
            }

            // כפתור מיקום יציאה
            holder.checkOutLocButton.setOnClickListener {
                val messageView = TextView(holder.itemView.context).apply {
                    text = record.checkOutLocation.ifBlank { "לא זמין" }
                    setTextColor(Color.BLACK)
                    textSize = 16f
                    setPadding(40, 40, 40, 40)
                }
                AlertDialog.Builder(holder.itemView.context)
                    .setTitle("מיקום יציאה")
                    .setView(messageView)
                    .setPositiveButton("סגור", null)
                    .show()
            }

            // כפתור הערה
            holder.commentButton.visibility = View.VISIBLE
            holder.commentButton.setOnClickListener {
                val commentText = record.comment.orEmpty()
                val messageView = TextView(holder.itemView.context).apply {
                    text = if (commentText.isNotBlank()) commentText else "אין הערה ליום זה"
                    setTextColor(Color.BLACK)
                    textSize = 16f
                    setPadding(40, 40, 40, 40)
                }
                AlertDialog.Builder(holder.itemView.context)
                    .setTitle("הערה ליום ${record.date}")
                    .setView(messageView)
                    .setPositiveButton("סגור", null)
                    .show()
            }

        } else if (holder is TotalViewHolder) {
            // שורת סיכום חודש
            holder.dateTV.text = "סה״כ לחודש"
            holder.checkInTV.text = record.checkIn      // נניח שזה סה"כ שעות
            holder.checkOutTV.text = ""
            holder.totalHoursTV.text = ""
        }
    }

    override fun getItemCount(): Int = records.size

    // ViewHolder לשורה רגילה
    class AttendanceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dateTV: TextView = itemView.findViewById(R.id.item_date)
        val checkInTV: TextView = itemView.findViewById(R.id.item_check_in)
        val checkOutTV: TextView = itemView.findViewById(R.id.item_check_out)
        val totalHoursTV: TextView = itemView.findViewById(R.id.item_hours)
        val checkInLocButton: ImageButton = itemView.findViewById(R.id.item_check_in_location_button)
        val checkOutLocButton: ImageButton = itemView.findViewById(R.id.item_check_out_location_button)
        val commentButton: ImageButton = itemView.findViewById(R.id.item_comment_button)
    }

    // ViewHolder לשורת סיכום
    class TotalViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dateTV: TextView = itemView.findViewById(R.id.item_date)
        val checkInTV: TextView = itemView.findViewById(R.id.item_check_in)
        val checkOutTV: TextView = itemView.findViewById(R.id.item_check_out)
        val totalHoursTV: TextView = itemView.findViewById(R.id.item_hours)

        init {
            dateTV.setTextColor(Color.BLACK)
            dateTV.setTypeface(null, Typeface.BOLD) // מדגיש את הטקסט
        }
    }
}
