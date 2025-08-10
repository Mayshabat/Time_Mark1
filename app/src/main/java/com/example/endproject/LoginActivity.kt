package com.example.endproject


import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.endproject.databinding.ActivityLoginBinding
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class LoginActivity : AppCompatActivity() {

    // משתנה עבור View Binding של activity_login.xml
    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // עיצוב – הגדרת מרווחים מהשוליים של המערכת
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // אם אין משתמש מחובר – לפתוח את מסך ההתחברות
        // אחרת – לבדוק תפקיד ולעבור לדף המתאים
        if (FirebaseAuth.getInstance().currentUser == null) {
            signIn()
        } else {
            checkUserRole()
        }
    }

    // מנגנון שמקבל תוצאה ממסך ההתחברות
    private val signInLauncher = registerForActivityResult(
        FirebaseAuthUIActivityResultContract()
    ) { res ->
        this.onSignInResult(res)
    }

    // פונקציית התחברות – מאפשרת למשתמש לבחור בין מייל וגוגל
    private fun signIn() {
        val providers = arrayListOf(
            AuthUI.IdpConfig.EmailBuilder().build(),
            AuthUI.IdpConfig.GoogleBuilder().build()
        )

        val signInIntent = AuthUI.getInstance()
            .createSignInIntentBuilder()
            .setAvailableProviders(providers)
            .setLogo(R.drawable.time_mark) // לוגו שיופיע במסך ההתחברות
            .setTheme(R.style.Theme_EndProject) // עיצוב
            .build()

        signInLauncher.launch(signInIntent)
    }

    // אחרי ניסיון התחברות – בודק אם הצליח
    private fun onSignInResult(result: FirebaseAuthUIAuthenticationResult) {
        if (result.resultCode == RESULT_OK) {
            checkUserData()
        } else {
            Toast.makeText(this, "שגיאה בהתחברות, מנסים שוב...", Toast.LENGTH_LONG).show()
            signIn()
        }
    }

    // בודק אם כבר נשמר שם המשתמש והתפקיד במסד הנתונים
    private fun checkUserData() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val uid = user.uid
        val dbRef = FirebaseDatabase.getInstance().getReference("users").child(uid)

        dbRef.get().addOnSuccessListener { snapshot ->
            val name = snapshot.child("name").getValue(String::class.java)
            val role = snapshot.child("role").getValue(String::class.java)

            // אם אין שם או תפקיד – צריך לבקש ולשמור אותם
            if (name.isNullOrBlank() || role.isNullOrBlank()) {
                val provider = user.providerData.firstOrNull { it.providerId != "firebase" }?.providerId ?: ""
                if (provider == "google.com") {
                    val displayName = user.displayName ?: "משתמש מגוגל"
                    saveUserToDatabase(uid, displayName)
                } else {
                    // שואל את המשתמש איך קוראים לו
                    askForName { inputName ->
                        saveUserToDatabase(uid, inputName)
                    }
                }
            } else {
                // אם כבר יש נתונים – בודק את התפקיד
                checkUserRole()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "שגיאה בגישה למסד הנתונים", Toast.LENGTH_SHORT).show()
            checkUserRole()
        }
    }

    // תיבת דיאלוג לבקשת שם מהמשתמש
    private fun askForName(onNameEntered: (String) -> Unit) {
        if (isFinishing || isDestroyed) return

        val editText = EditText(this)
        editText.hint = "הכנס שם"

        AlertDialog.Builder(this)
            .setTitle("שם משתמש")
            .setMessage("אנא הזן את שמך")
            .setView(editText)
            .setCancelable(false)
            .setPositiveButton("אישור") { _, _ ->
                val name = editText.text.toString().ifBlank { "משתמש ללא שם" }
                onNameEntered(name)
            }
            .setNegativeButton("ביטול") { dialog, _ ->
                dialog.dismiss()
                onNameEntered("משתמש ללא שם")
            }
            .show()
    }

    // שומר את שם המשתמש והתפקיד (ברירת מחדל: עובד) ב־Firebase
    private fun saveUserToDatabase(uid: String, name: String) {
        val dbRef = FirebaseDatabase.getInstance().getReference("users").child(uid)
        val userData = mapOf(
            "name" to name,
            "role" to "employee"
        )
        dbRef.setValue(userData)
        checkUserRole()
    }

    // לפי התפקיד שנשמר ב־Firebase – עובר למסך המתאים
    private fun checkUserRole() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val userRef = FirebaseDatabase.getInstance().getReference("users").child(uid)

        userRef.child("role").get().addOnSuccessListener {
            val role = it.getValue(String::class.java)
            if (role == "admin") {
                // אם מנהל – עובר ל־ManagerActivity
                startActivity(Intent(this, ManagerActivity::class.java))
            } else {
                // אם עובד – עובר ל־MainActivity
                startActivity(Intent(this, MainActivity::class.java))
            }
            finish()
        }.addOnFailureListener {
            Toast.makeText(this, "שגיאה בזיהוי משתמש", Toast.LENGTH_SHORT).show()
        }
    }
}
