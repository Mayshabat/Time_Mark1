package com.example.endproject.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.endproject.R

/**
 * מתאם (Adapter) להצגת טפסים ברשימה (RecyclerView), כל טופס כולל שם וכפתור לפתיחה.
 * משמש להצגת טפסי שירות או דוחות PDF שהועלו.
 */
class FormsAdapter(
    private val forms: List<Pair<String, String>>, // רשימת זוגות: שם הטופס וכתובת ה־URL
    private val onFormClick: (String) -> Unit      // פעולה שמתבצעת כשלוחצים על כפתור פתיחה
) : RecyclerView.Adapter<FormsAdapter.FormViewHolder>() {

    // ViewHolder שמייצג שורה אחת בטבלה: שם הטופס וכפתור לפתיחה
    class FormViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val formNameText: TextView = view.findViewById(R.id.formNameText)      // טקסט המציג את שם הטופס
        val openButton: Button = view.findViewById(R.id.openFormButton)        // כפתור לפתיחת הטופס
    }

    // יוצרת תצוגה חדשה לשורת טופס (מ־item_form.xml)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FormViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_form, parent, false)
        return FormViewHolder(view)
    }

    // קישור הנתונים לתצוגה (שם + פעולת לחיצה)
    override fun onBindViewHolder(holder: FormViewHolder, position: Int) {
        val (name, url) = forms[position]                 // מפצל את ה־Pair לשם ו־URL
        holder.formNameText.text = name                  // מציג את שם הטופס בטקסט
        holder.openButton.setOnClickListener {
            onFormClick(url)                             // קורא לפעולה שהועברה מבחוץ עם ה־URL
        }
    }

    // מחזיר את מספר הפריטים ברשימה
    override fun getItemCount(): Int = forms.size
}
