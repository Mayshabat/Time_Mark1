package com.example.endproject

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*
import com.example.endproject.adapters.FormsAdapter

class PdfViewActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FormsAdapter
    private val formList = mutableListOf<Pair<String, String>>() // name to url

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_pdf)

        recyclerView = findViewById(R.id.formsRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = FormsAdapter(formList) { url ->
            openPdfFromUrl(url)
        }

        recyclerView.adapter = adapter
        loadForms()
    }

    private fun loadForms() {
        val ref = FirebaseDatabase.getInstance().getReference("pdfForms")
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                formList.clear()
                for (child in snapshot.children) {
                    val name = child.child("name").getValue(String::class.java) ?: continue
                    val url = child.child("url").getValue(String::class.java) ?: continue
                    formList.add(name to url)
                }
                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun openPdfFromUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(url), "application/pdf")
                flags = Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "לא נמצאה אפליקציה לפתיחת קובץ PDF", Toast.LENGTH_SHORT).show()
        }
    }
}
