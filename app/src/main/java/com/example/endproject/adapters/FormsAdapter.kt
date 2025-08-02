package com.example.endproject.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.endproject.R

class FormsAdapter(
    private val forms: List<Pair<String, String>>, // רשימת זוגות: שם הטופס וכתובת ה־URL
    private val onFormClick: (String) -> Unit      // פעולה שמתבצעת כשלוחצים על כפתור פתיחה
) : RecyclerView.Adapter<FormsAdapter.FormViewHolder>() {

    class FormViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val formNameText: TextView = view.findViewById(R.id.formNameText)
        val openButton: Button = view.findViewById(R.id.openFormButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FormViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_form, parent, false)
        return FormViewHolder(view)
    }

    override fun onBindViewHolder(holder: FormViewHolder, position: Int) {
        val (name, url) = forms[position]
        holder.formNameText.text = name
        holder.openButton.setOnClickListener {
            onFormClick(url)
        }
    }

    override fun getItemCount(): Int = forms.size
}
