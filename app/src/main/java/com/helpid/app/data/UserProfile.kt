package com.helpid.app.data

import java.util.Date

data class EmergencyContactData(
    val name: String = "",
    val phone: String = ""
)

data class UserProfile(
    val userId: String = "",
    val name: String = "",
    val bloodGroup: String = "",
    val address: String = "",
    val allergies: List<String> = emptyList(),
    val medicalNotes: List<String> = emptyList(),
    val emergencyContacts: List<EmergencyContactData> = emptyList(),
    val language: String = "en",
    val lastUpdated: Long = System.currentTimeMillis()
) {
    // Convenience method to convert to Firestore format
    fun toMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "name" to name,
        "bloodGroup" to bloodGroup,
        "address" to address,
        "allergies" to allergies,
        "medicalNotes" to medicalNotes,
        "emergencyContacts" to emergencyContacts.map { 
            mapOf(
                "name" to it.name,
                "phone" to it.phone
            )
        },
        "language" to language,
        "lastUpdated" to lastUpdated
    )

    companion object {
        // Default profile for new users
        fun default(userId: String = "", language: String = "en"): UserProfile {
            if (language == "vi") {
                return UserProfile(
                    userId = userId,
                    name = "Nguyễn Văn A",
                    bloodGroup = "O+",
                    address = "",
                    allergies = emptyList(),
                    medicalNotes = listOf(
                        "Tiểu đường",
                        "Cao huyết áp"
                    ),
                    emergencyContacts = listOf(
                        EmergencyContactData("Cha", "+84 000 000 001"),
                        EmergencyContactData("Mẹ", "+84 000 000 002")
                    ),
                    language = language
                )
            }

            return UserProfile(
                userId = userId,
                name = "John Doe",
                bloodGroup = "O+",
                address = "",
                allergies = emptyList(),
                medicalNotes = listOf(
                    "Diabetic",
                    "High Blood Pressure"
                ),
                emergencyContacts = listOf(
                    EmergencyContactData("Father", "+91 98765 43210"),
                    EmergencyContactData("Mother", "+91 87654 32109")
                ),
                language = language
            )
        }
    }
}
