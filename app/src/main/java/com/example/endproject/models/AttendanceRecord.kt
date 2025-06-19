package com.example.endproject.models

data class AttendanceRecord(
    val date: String ,
    val checkIn: String ,
    val checkOut: String ,
    val checkInLocation: String,
    val checkOutLocation:String,
    val totalHours: String = "-",
    val comment: String? = null
)
