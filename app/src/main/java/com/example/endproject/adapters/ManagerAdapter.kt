package com.example.endproject.adapters

import android.app.AlertDialog
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.endproject.R
import com.example.endproject.models.EmployeeDayRecord

// מתאם להצגת רשימת ימי עבודה של עובד עבור המנהל
class ManagerAdapter(private val records: List<EmployeeDayRecord>) :
    RecyclerView.Adapter<ManagerAdapter.RecordViewHolder>() {

    // מחזיק תצוגה לכל שורה ברשימה (ViewHolder)
    class RecordViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTV: TextView = itemView.findViewById(R.id.item_name)
        val dateTV: TextView = itemView.findViewById(R.id.item_date)
        val checkInTV: TextView = itemView.findViewById(R.id.item_check_in)
        val checkOutTV: TextView = itemView.findViewById(R.id.item_check_out)
        val hoursTV: TextView = itemView.findViewById(R.id.item_hours)
        val checkInLocButton: ImageButton = itemView.findViewById(R.id.item_check_in_location_button)
        val checkOutLocButton: ImageButton = itemView.findViewById(R.id.item_check_out_location_button)
        val commentButton: ImageButton = itemView.findViewById(R.id.item_comment_button)
    }

    // יצירת ViewHolder חדש לפי תצוגת XML
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_attendance, parent, false)
        return RecordViewHolder(view)
    }

    // קישור בין הנתונים לבין התצוגה
    override fun onBindViewHolder(holder: RecordViewHolder, position: Int) {
        val record = records[position]

        // הצגת נתונים בסיסיים
        holder.nameTV.text = record.employeeName
        holder.dateTV.text = record.date
        holder.checkInTV.text = record.checkIn
        holder.checkOutTV.text = record.checkOut
        holder.hoursTV.text = record.totalHours

        // לחיצה על כפתור מיקום כניסה
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

        // לחיצה על כפתור מיקום יציאה
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

        // לחיצה על כפתור הערה
        holder.commentButton.setOnClickListener {
            val commentText = record.comment?.trim().orEmpty()
            val messageView = TextView(holder.itemView.context).apply {
                text = if (commentText.isNotEmpty()) commentText else "אין הערה ליום זה"
                setTextColor(Color.BLACK)
                textSize = 16f
                setPadding(40, 40, 40, 40)
            }

            AlertDialog.Builder(holder.itemView.context)
                .setTitle(":הערה ליום ${record.date}")
                .setView(messageView)
                .setPositiveButton("סגור", null)
                .show()
        }
    }

    // מספר הפריטים ברשימה
    override fun getItemCount(): Int = records.size
}
