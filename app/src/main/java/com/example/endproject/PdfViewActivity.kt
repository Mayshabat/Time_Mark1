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

/**
 * PdfViewActivity:
 * מסך שמציג רשימת טפסי PDF (שם + קישור) מתוך Realtime Database בענף "pdfForms".
 * בלחיצה על פריט ברשימה נפתחת אפליקציה חיצונית לצפייה ב-PDF לפי ה-URL.
 */
class PdfViewActivity : AppCompatActivity() {

    // RecyclerView להצגת הרשימה
    private lateinit var recyclerView: RecyclerView
    // מתאם (Adapter) שמקבל רשימת זוגות (שם, קישור) ומטפל בלחיצה לפתיחת ה-PDF
    private lateinit var adapter: FormsAdapter

    // רשימת הטפסים שתוצג: Pair<name, url>
    private val formList = mutableListOf<Pair<String, String>>() // name to url

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_pdf) // קישור לקובץ ה-XML של המסך

        // קישור רכיבי UI מה-XML
        recyclerView = findViewById(R.id.formsRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this) // תצוגה אנכית פשוטה

        // בניית האדפטר: מקבל את הרשימה + Lambda לפתיחת קובץ ברגע שלוחצים
        adapter = FormsAdapter(formList) { url ->
            openPdfFromUrl(url) // פעולה שתפתח את ה-PDF לפי URL
        }

        recyclerView.adapter = adapter

        // טעינת הטפסים מה-Realtime Database
        loadForms()
    }

    /**
     * טוען את רשימת הטפסים מ-Firebase Realtime Database תחת "pdfForms".
     * לכל פריט מצופה שיהיו שדות: "name" (שם הקובץ להצגה) ו-"url" (קישור הציבורי לקובץ).
     * הערה: אם במערכת שלך נשמרים רק url/uploader/type/timestamp — תוודאי שגם "name" נשמר,
     * אחרת הרשימה תהיה ריקה (כאן אנחנו ממשיכים לפי הדרישה "לא לשנות קוד", רק מסבירים).
     */
    private fun loadForms() {
        val ref = FirebaseDatabase.getInstance().getReference("pdfForms")
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                formList.clear() // איפוס לפני מילוי חדש

                // מעבר על כל הטפסים בענף
                for (child in snapshot.children) {
                    val name = child.child("name").getValue(String::class.java) ?: continue // שם לתצוגה
                    val url = child.child("url").getValue(String::class.java) ?: continue  // קישור להורדה/צפייה
                    formList.add(name to url) // הוספת זוג (שם, קישור) לרשימה
                }

                // עדכון תצוגה אחרי טעינה
                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                // אפשר להוסיף כאן לוג/טוסט במקרה של שגיאה בקריאה מה-DB (השארתי ריק לפי הקוד המקורי)
            }
        })
    }

    /**
     * openPdfFromUrl:
     * יוצר Intent לצפייה בקובץ PDF לפי URL.
     * אם אין במכשיר אפליקציה שיודעת לטפל ב-PDF, נתפוס ActivityNotFoundException ונראה Toast.
     */
    private fun openPdfFromUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(url), "application/pdf") // מציין שמדובר ב-PDF
                // FLAG_ACTIVITY_NO_HISTORY: לא לשמור את הפעילות בהיסטוריה
                // FLAG_GRANT_READ_URI_PERMISSION: מתן הרשאת קריאה (למקרה של URI שמצריך הרשאה)
                flags = Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            startActivity(intent) // ניסיון לפתוח באפליקציית PDF קיימת במכשיר
        } catch (e: ActivityNotFoundException) {
            // לא קיימת אפליקציה מתאימה לפתיחת PDF במכשיר
            Toast.makeText(this, "לא נמצאה אפליקציה לפתיחת קובץ PDF", Toast.LENGTH_SHORT).show()
        }
    }
}
