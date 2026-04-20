package com.menupoints.app

import android.app.Dialog
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

class NoteDialogFragment : DialogFragment() {

    private lateinit var pointName: String
    private lateinit var initialNote: String
    private lateinit var onSave: (String) -> Unit

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pointName = arguments?.getString("pointName") ?: ""
        initialNote = arguments?.getString("initialNote") ?: ""
        @Suppress("UNCHECKED_CAST")
        onSave = arguments?.getSerializable("onSave") as? (String) -> Unit ?: {}
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.dialog_note, null)

        val pointNameText = view.findViewById<TextView>(R.id.dialogPointName)
        val noteEditText = view.findViewById<EditText>(R.id.dialogNoteEditText)
        val cancelButton = view.findViewById<Button>(R.id.dialogCancelButton)
        val saveButton = view.findViewById<Button>(R.id.dialogSaveButton)

        pointNameText.text = pointName
        noteEditText.setText(initialNote)

        cancelButton.setOnClickListener {
            dismiss()
        }

        saveButton.setOnClickListener {
            val note = noteEditText.text.toString()
            onSave(note)
            dismiss()
        }

        builder.setView(view)
        return builder.create()
    }

    companion object {
        fun newInstance(pointName: String, initialNote: String, onSave: (String) -> Unit): NoteDialogFragment {
            val fragment = NoteDialogFragment()
            fragment.arguments = Bundle().apply {
                putString("pointName", pointName)
                putString("initialNote", initialNote)
                putSerializable("onSave", onSave as java.io.Serializable)
            }
            return fragment
        }
    }
}