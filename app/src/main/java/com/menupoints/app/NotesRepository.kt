package com.menupoints.app

import com.google.firebase.database.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class NotesRepository {
    private val database = FirebaseDatabase.getInstance()
    private val notesRef = database.getReference("notes")

    fun getNote(pointId: String): Flow<String> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val note = snapshot.getValue(String::class.java) ?: ""
                trySend(note)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        notesRef.child(pointId).addValueEventListener(listener)
        awaitClose { notesRef.child(pointId).removeEventListener(listener) }
    }

    suspend fun saveNote(pointId: String, note: String) {
        if (note.isBlank()) {
            notesRef.child(pointId).setValue(null)
        } else {
            notesRef.child(pointId).setValue(note)
        }
    }
}