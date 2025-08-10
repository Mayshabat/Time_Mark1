package com.example.endproject


import androidx.core.app.ActivityCompat
import android.content.pm.PackageManager
import android.location.Location
import android.location.Geocoder
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.android.gms.location.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var userName: TextView
    private lateinit var lastEntry: TextView
    private lateinit var currentTime: TextView
    private lateinit var reportEntryButton: ImageButton
    private lateinit var reportExitButton: ImageButton
    private lateinit var personalAreaButton: Button
    private lateinit var addCommentButton: Button
    private lateinit var serviceCallButton: Button
    private lateinit var uid: String

    //  משתנים לעבודה עם מיקום ומסד הנתונים
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var database: DatabaseReference

    //  הגדרות לשעון ותאריך
    private val handler = Handler(Looper.getMainLooper()) // רץ כל שנייה לעדכון השעון
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

    //  מתבצע בעת טעינת המסך
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendance_entry) // קושר את ה-XML

        // שמירת UID של המשתמש המחובר
        uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""

        // קישור כל רכיבי ה-UI מה-XML למשתנים בקוד
        userName = findViewById(R.id.userName)
        lastEntry = findViewById(R.id.lastEntry)
        currentTime = findViewById(R.id.currentTime)
        reportEntryButton = findViewById(R.id.reportEntryButton)
        reportExitButton = findViewById(R.id.reportExitButton)
        personalAreaButton = findViewById(R.id.personalAreaButton)
        addCommentButton = findViewById(R.id.addCommentButton)
        serviceCallButton = findViewById(R.id.serviceCallButton)
        val logoutButton = findViewById<Button>(R.id.logoutButton)

        // התחברות ל-Firebase Database
        database = FirebaseDatabase.getInstance().reference

        // אתחול רכיב המיקום
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        showUserName() // מציג את שם המשתמש
        updateLiveClock() // מפעיל שעון חי
        findViewById<TextView>(R.id.reportDate).text = dateFormat.format(Date()) // מציג את תאריך היום
        loadTodayStatus() // טוען את הסטטוס של הכניסה/יציאה להיום

        // דיווח כניסה
        reportEntryButton.setOnClickListener { markAttendance("checkIn") }
        // דיווח יציאה
        reportExitButton.setOnClickListener { markAttendance("checkOut") }

        // מעבר לאזור אישי
        personalAreaButton.setOnClickListener {
            val calendar = Calendar.getInstance()
            val currentYear = calendar.get(Calendar.YEAR).toString()
            val currentMonth = String.format("%02d", calendar.get(Calendar.MONTH) + 1)

            val intent = Intent(this, HistoryActivity::class.java)
            intent.putExtra("YEAR", currentYear)
            intent.putExtra("MONTH", currentMonth)
            startActivity(intent)
        }

        // מעבר למסך הוספת הערה
        addCommentButton.setOnClickListener {
            val intent = Intent(this, CommentActivity::class.java)
            startActivity(intent)
        }

        // מעבר למסך טופס קריאת שירות
        serviceCallButton.setOnClickListener {
            val intent = Intent(this, ServiceFormActivity::class.java)
            startActivity(intent)
        }

        // יציאה מהחשבון וחזרה למסך התחברות
        logoutButton.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    //  מציג שם משתמש מה-Firebase
    private fun showUserName() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val userRef = FirebaseDatabase.getInstance().getReference("users").child(uid)

        userRef.child("name").get().addOnSuccessListener {
            val name = it.getValue(String::class.java) ?: "משתמש"
            userName.text = name
        }.addOnFailureListener {
            userName.text = "משתמש"
        }
    }

    //  שעון שמתעדכן כל שנייה
    private fun updateLiveClock() {
        handler.post(object : Runnable {
            override fun run() {
                currentTime.text = timeFormat.format(Date())
                handler.postDelayed(this, 1000)
            }
        })
    }

    //  מחזיר את הנתיב ב-Firebase ליום הנוכחי
    private fun getTodayPath(): DatabaseReference {
        val today = dateFormat.format(Date())
        return database.child("attendance").child(uid).child(today)
    }

    //  דיווח כניסה/יציאה כולל בקשת מיקום
    private fun markAttendance(type: String) {
        if (uid.isBlank()) {
            Toast.makeText(this, "משתמש לא מחובר", Toast.LENGTH_SHORT).show()
            return
        }

        // בדיקת הרשאות מיקום
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            // בקשת הרשאות
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                1001
            )
            return
        }

        // קבלת מיקום עדכני
        requestFreshLocation { location ->
            if (location == null) {
                Toast.makeText(this, "לא ניתן לקבל מיקום נוכחי", Toast.LENGTH_SHORT).show()
            } else {
                handleAttendanceWithLocation(location, type)
            }
        }
    }

    //  תגובה לבקשת הרשאות
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "הרשאת מיקום אושרה", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "יש לאשר הרשאת מיקום כדי לבצע דיווח", Toast.LENGTH_SHORT).show()
        }
    }

    //  טעינת סטטוס כניסה/יציאה להיום
    private fun loadTodayStatus() {
        val todayPath = getTodayPath()
        todayPath.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val checkInTime = snapshot.child("checkIn/time").getValue(String::class.java)
                val checkOutTime = snapshot.child("checkOut/time").getValue(String::class.java)
                val today = dateFormat.format(Date())

                lastEntry.text = when {
                    checkOutTime != null -> "יציאה אחרונה  $today בשעה $checkOutTime"
                    checkInTime != null -> "כניסה אחרונה  $today בשעה $checkInTime"
                    else -> "אין רישום להיום"
                }

                // מנטרל את הכפתור אם כבר נרשם
                reportEntryButton.isEnabled = checkInTime == null
                reportExitButton.isEnabled = checkOutTime == null
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MainActivity, "שגיאה בטעינת נתונים", Toast.LENGTH_SHORT).show()
            }
        })
    }

    //  שמירת דיווח נוכחות עם מיקום
    private fun handleAttendanceWithLocation(location: Location, type: String) {
        val fullDateTimeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val now = fullDateTimeFormat.format(Date())
        val todayPath = getTodayPath()

        todayPath.child(type).get().addOnSuccessListener {
            if (it.exists()) {
                Toast.makeText(this, "כבר נרשמה $type להיום", Toast.LENGTH_SHORT).show()
            } else {
                val geocoder = Geocoder(this, Locale.getDefault())
                val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                val addressText = if (!addresses.isNullOrEmpty()) {
                    addresses[0].getAddressLine(0) ?: "מיקום לא ידוע"
                } else {
                    "מיקום לא ידוע"
                }

                // מפת הנתונים לשמירה
                val attendanceMap = mapOf(
                    "time" to now,
                    "location" to addressText
                )

                // שמירה ב-Firebase
                todayPath.child(type).setValue(attendanceMap)
                    .addOnSuccessListener {
                        Toast.makeText(this, "$type נרשמה בשעה $now", Toast.LENGTH_SHORT).show()
                        loadTodayStatus()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "שגיאה בשמירה ל־Firebase", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    //  בקשת מיקום עדכני
    private fun requestFreshLocation(onLocationReceived: (Location?) -> Unit) {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
            .setMaxUpdates(1)
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                onLocationReceived(result.lastLocation)
                fusedLocationClient.removeLocationUpdates(this)
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                if (!availability.isLocationAvailable) {
                    onLocationReceived(null)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    //  עצירת השעון כשעוזבים את המסך
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
