package com.menupoints.app

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*
import com.menupoints.app.models.MenuItem
import com.menupoints.app.models.Point
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStream
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.widget.ImageButton

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var pointsRecyclerView: RecyclerView
    private lateinit var menuRecyclerView: RecyclerView
    private lateinit var pointTitle: TextView
    private lateinit var overlay: View
    private lateinit var saveButton: ImageButton

    private val notesRepository = NotesRepository()
    private val database = FirebaseDatabase.getInstance()
    private val pointsRef = database.getReference("points")
    private val menusRef = database.getReference("menus")
    private val marksRef = database.getReference("marks")
    private val pendingMenuRef = database.getReference("pendingMenu")

    private val pointsList = mutableListOf<Point>()
    private var currentPointId: String? = null
    private var currentMenuItems = listOf<MenuItem>()
    private val currentMarks = mutableMapOf<String, Int>()

    private var pointsAdapter: PointsAdapter? = null
    private var menuAdapter: MenuAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkStoragePermission()

        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
        window.statusBarColor = android.graphics.Color.TRANSPARENT

        drawerLayout = findViewById(R.id.drawerLayout)
        pointsRecyclerView = findViewById(R.id.pointsRecyclerView)
        menuRecyclerView = findViewById(R.id.menuRecyclerView)
        pointTitle = findViewById(R.id.pointTitle)
        overlay = findViewById(R.id.overlay)
        saveButton = findViewById(R.id.saveButton)

        pointsRecyclerView.layoutManager = LinearLayoutManager(this)
        menuRecyclerView.layoutManager = LinearLayoutManager(this)

        findViewById<View>(R.id.menuButton).setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
            overlay.visibility = View.VISIBLE
        }

        overlay.setOnClickListener {
            drawerLayout.closeDrawers()
            overlay.visibility = View.GONE
        }

        findViewById<View>(R.id.uploadButton).setOnClickListener {
            if (currentPointId != null) {
                selectExcelFile()
            } else {
                Toast.makeText(this, "Сначала создайте точку", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<View>(R.id.resetButton).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Сброс")
                .setMessage("Сбросить все отметки для текущей точки?")
                .setPositiveButton("Да") { _, _ ->
                    if (currentPointId != null) {
                        marksRef.child(currentPointId!!).setValue(null)
                        currentMarks.clear()
                        menuAdapter?.updateItems(currentMenuItems, currentMarks)
                        Toast.makeText(this, "Отметки сброшены", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Нет", null)
                .show()
        }

        saveButton.setOnClickListener {
            saveCurrentMarks()
        }

        findViewById<View>(R.id.addPointButton).setOnClickListener {
            showCreatePointDialog()
        }

        loadPoints()
        listenForPendingMenu()


    }

    private fun checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, "android.permission.READ_MEDIA_DOCUMENTS")
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    arrayOf("android.permission.READ_MEDIA_DOCUMENTS"), 101)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 101)
            }
        }
    }

    private fun saveCurrentMarks() {
        if (currentPointId == null || menuAdapter == null) return

        val newMarks = menuAdapter!!.getAllMarks()
        val oldMarks = currentMarks.toMutableMap()

        // Находим изменения
        val changes = mutableListOf<String>()
        val allKeys = newMarks.keys + oldMarks.keys
        allKeys.forEach { key ->
            val oldValue = oldMarks[key] ?: 0
            val newValue = newMarks[key] ?: 0
            if (oldValue != newValue) {
                changes.add("$key: $oldValue→$newValue")
            }
        }

        if (changes.isNotEmpty()) {
            val logRef = database.getReference("adminLogs").push()
            logRef.setValue(mapOf(
                "timestamp" to System.currentTimeMillis(),
                "date" to java.text.SimpleDateFormat("dd.MM.yyyy, HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
                "action" to "marks_changed",
                "details" to "Точка: ${pointTitle.text}, ${changes.joinToString(", ")}",
                "userId" to "android"
            ))
        }

        // ... существующий код сохранения
        val updates = mutableMapOf<String, Any>()
        for ((key, value) in newMarks) {
            updates[key] = value
        }
        marksRef.child(currentPointId!!).setValue(updates)
    }

    private fun listenForPendingMenu() {
        pendingMenuRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.child("status").getValue(String::class.java)
                if (status == "pending") {
                    val menuSnapshot = snapshot.child("menu")
                    if (menuSnapshot.exists() && menuSnapshot.childrenCount > 0) {
                        showConfirmDialog(menuSnapshot)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun showConfirmDialog(menuSnapshot: DataSnapshot) {
        val itemsCount = menuSnapshot.childrenCount

        AlertDialog.Builder(this)
            .setTitle("Предложено новое меню")
            .setMessage("Предложено новое меню ($itemsCount блюд).\n\nПрименить для ВСЕХ точек?")
            .setPositiveButton("Применить") { _, _ ->
                applyMenuToAllPoints(menuSnapshot)
            }
            .setNegativeButton("Отклонить") { _, _ ->
                pendingMenuRef.removeValue()
            }
            .setNeutralButton("Просмотреть") { _, _ ->
                showMenuPreview(menuSnapshot)
            }
            .show()
    }

    private fun showMenuPreview(menuSnapshot: DataSnapshot) {
        val categoryOrder = listOf("ЗАВТРАКИ", "САЛАТЫ", "ПЕРВЫЕ БЛЮДА", "ВТОРЫЕ БЛЮДА", "ДЕСЕРТЫ", "СНЭКИ")
        val grouped = mutableMapOf<String, MutableList<String>>()

        for (child in menuSnapshot.children) {
            val name = child.child("name").getValue(String::class.java) ?: ""
            val category = child.child("category").getValue(String::class.java) ?: ""
            if (!grouped.containsKey(category)) grouped[category] = mutableListOf()
            grouped[category]?.add(name)
        }

        val previewText = StringBuilder()
        for (cat in categoryOrder) {
            grouped[cat]?.let { items ->
                previewText.append("\n📌 $cat:\n")
                items.forEach { previewText.append("   🍽️ $it\n") }
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Предпросмотр меню")
            .setMessage(previewText.toString())
            .setPositiveButton("Назад", null)
            .show()
    }

    private fun applyMenuToAllPoints(menuSnapshot: DataSnapshot) {
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("Обновление")
            .setMessage("Обновляем меню для всех точек...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        pointsRef.get().addOnSuccessListener { pointsSnapshot ->
            val updates = mutableMapOf<String, Any>()
            val pointsList = pointsSnapshot.children.toList()

            for (point in pointsList) {
                val pointId = point.key ?: continue
                val itemsMap = mutableMapOf<String, Any>()
                for (child in menuSnapshot.children) {
                    val id = child.child("id").getValue(String::class.java) ?: ""
                    val category = child.child("category").getValue(String::class.java) ?: ""
                    val name = child.child("name").getValue(String::class.java) ?: ""
                    val defaultValue = child.child("defaultValue").getValue(Int::class.java) ?: 0
                    itemsMap[id] = mapOf(
                        "id" to id,
                        "category" to category,
                        "name" to name,
                        "defaultValue" to defaultValue
                    )
                }
                updates["menus/$pointId"] = itemsMap
            }

            database.reference.updateChildren(updates).addOnSuccessListener {
                pendingMenuRef.removeValue()
                progressDialog.dismiss()
                Toast.makeText(this, "Меню обновлено для ${pointsList.size} точек", Toast.LENGTH_LONG).show()
                currentPointId?.let {
                    loadMenuForPoint(it)
                    loadMarksForPoint(it)
                }
            }.addOnFailureListener { e ->
                progressDialog.dismiss()
                Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadPoints() {
        pointsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                pointsList.clear()
                for (child in snapshot.children) {
                    val name = child.child("name").getValue(String::class.java) ?: "Точка"
                    pointsList.add(Point(child.key ?: "", name))
                }

                if (pointsList.isEmpty()) {
                    pointsRef.push().setValue(mapOf("name" to "Основная точка"))
                } else if (currentPointId == null && pointsList.isNotEmpty()) {
                    selectPoint(pointsList[0])
                }

                updatePointsList()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MainActivity, "Ошибка: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }



    private fun showNoteDialog(point: Point) {
        lifecycleScope.launch {
            var currentNote = ""
            notesRepository.getNote(point.id).collect { note ->
                currentNote = note
            }

            val dialog = NoteDialogFragment.newInstance(
                pointName = point.name,
                initialNote = currentNote,
                onSave = { newNote ->
                    lifecycleScope.launch {
                        notesRepository.saveNote(point.id, newNote)
                        Toast.makeText(this@MainActivity, "Заметка сохранена", Toast.LENGTH_SHORT).show()
                    }
                }
            )
            dialog.show(supportFragmentManager, "NoteDialog")
        }
    }

    private fun updatePointsList() {
        pointsAdapter = PointsAdapter(
            pointsList,
            { point -> selectPoint(point) },
            { point -> showRenameDialog(point) },
            { point -> deletePoint(point) },
            { point -> showNoteDialog(point) },
            currentPointId
        )
        pointsRecyclerView.adapter = pointsAdapter
    }

    private fun selectPoint(point: Point) {
        currentPointId = point.id
        pointTitle.text = point.name
        drawerLayout.closeDrawers()
        overlay.visibility = View.GONE

        loadMenuForPoint(point.id)
        loadMarksForPoint(point.id)
        updatePointsList()
    }

    private fun loadMenuForPoint(pointId: String) {
        menusRef.child(pointId).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val items = mutableListOf<MenuItem>()
                for (child in snapshot.children) {
                    val id = child.child("id").getValue(String::class.java) ?: ""
                    val category = child.child("category").getValue(String::class.java) ?: ""
                    val name = child.child("name").getValue(String::class.java) ?: ""
                    val defaultValue = child.child("defaultValue").getValue(Int::class.java) ?: 0
                    items.add(MenuItem(id, category, name, defaultValue))
                }
                currentMenuItems = items
                updateMenuDisplay()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun loadMarksForPoint(pointId: String) {
        marksRef.child(pointId).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                currentMarks.clear()
                for (child in snapshot.children) {
                    val value = child.getValue(Int::class.java) ?: 0
                    currentMarks[child.key!!] = value
                }
                updateMenuDisplay()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun updateMenuDisplay() {
        if (currentMenuItems.isEmpty()) {
            menuRecyclerView.adapter = null
            menuAdapter = null
            return
        }

        menuAdapter = MenuAdapter(currentMenuItems, currentMarks)
        menuRecyclerView.adapter = menuAdapter
    }

    private fun showCreatePointDialog() {
        val input = EditText(this)
        input.hint = "Название точки"
        AlertDialog.Builder(this)
            .setTitle("Новая точка")
            .setView(input)
            .setPositiveButton("Создать") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    pointsRef.push().setValue(mapOf("name" to name))
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showRenameDialog(point: Point) {
        val input = EditText(this)
        input.setText(point.name)
        AlertDialog.Builder(this)
            .setTitle("Переименовать")
            .setView(input)
            .setPositiveButton("Сохранить") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    pointsRef.child(point.id).child("name").setValue(newName)
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun deletePoint(point: Point) {
        AlertDialog.Builder(this)
            .setTitle("Удалить точку")
            .setMessage("Удалить «${point.name}»?")
            .setPositiveButton("Удалить") { _, _ ->
                pointsRef.child(point.id).removeValue()
                menusRef.child(point.id).removeValue()
                marksRef.child(point.id).removeValue()
                if (currentPointId == point.id) {
                    currentPointId = null
                    pointTitle.text = "Точки"
                    currentMenuItems = emptyList()
                    currentMarks.clear()
                    updateMenuDisplay()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun selectExcelFile() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        startActivityForResult(Intent.createChooser(intent, "Выберите Excel файл"), 100)
    }

    @Deprecated("Deprecated")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            parseExcelAndSave(uri)
        }
    }

    private fun parseExcelAndSave(uri: Uri) {
        try {
            val inputStream: InputStream = contentResolver.openInputStream(uri) ?: return
            val workbook = WorkbookFactory.create(inputStream)
            val sheet = workbook.getSheetAt(0)

            val categoryList = listOf("ЗАВТРАКИ", "САЛАТЫ", "ПЕРВЫЕ БЛЮДА", "ВТОРЫЕ БЛЮДА", "ДЕСЕРТЫ", "СНЭКИ")
            var currentCategory = ""
            val items = mutableListOf<MenuItem>()

            val lastRowNum = minOf(sheet.lastRowNum, 500)

            for (rowIndex in 0..lastRowNum) {
                val row = sheet.getRow(rowIndex) ?: continue
                val firstCell = try { row.getCell(0)?.toString()?.trim() ?: "" } catch(_: Exception) { "" }
                val secondCell = try { row.getCell(1)?.toString()?.trim() ?: "" } catch(_: Exception) { "" }

                if (firstCell.isBlank()) continue

                val isCategory = categoryList.any { firstCell.uppercase().contains(it) }

                if (isCategory) {
                    currentCategory = categoryList.find { firstCell.uppercase().contains(it) } ?: ""
                    continue
                }

                if (currentCategory.isNotEmpty() && firstCell.length > 1) {
                    val defaultValue = secondCell.toIntOrNull() ?: 0
                    val itemId = "${currentCategory}_${firstCell}"
                        .replace(Regex("[^A-Za-zА-Яа-я0-9_]"), "")
                        .lowercase()
                    items.add(MenuItem(itemId, currentCategory, firstCell, defaultValue))
                }
            }

            if (items.isNotEmpty()) {
                applyMenuToAllPointsFromExcel(items)
                Toast.makeText(this, "Загружено ${items.size} блюд для всех точек", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Не найдено блюд", Toast.LENGTH_SHORT).show()
            }

            workbook.close()
            inputStream.close()

        } catch (e: OutOfMemoryError) {
            Toast.makeText(this, "Файл слишком большой. Разбейте Excel на части", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun applyMenuToAllPointsFromExcel(items: List<MenuItem>) {
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("Обновление")
            .setMessage("Загружаем меню для всех точек...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        pointsRef.get().addOnSuccessListener { pointsSnapshot ->
            val updates = mutableMapOf<String, Any>()
            val pointsList = pointsSnapshot.children.toList()

            for (point in pointsList) {
                val pointId = point.key ?: continue
                val menuMap = mutableMapOf<String, Any>()
                items.forEach { item ->
                    menuMap[item.id] = mapOf(
                        "id" to item.id,
                        "category" to item.category,
                        "name" to item.name,
                        "defaultValue" to item.defaultValue
                    )
                }
                updates["menus/$pointId"] = menuMap
            }

            database.reference.updateChildren(updates).addOnSuccessListener {
                progressDialog.dismiss()
                Toast.makeText(this, "Меню обновлено для ${pointsList.size} точек", Toast.LENGTH_LONG).show()
                currentPointId?.let {
                    loadMenuForPoint(it)
                    loadMarksForPoint(it)
                }
            }.addOnFailureListener { e ->
                progressDialog.dismiss()
                Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener { e ->
            progressDialog.dismiss()
            Toast.makeText(this, "Не удалось загрузить список точек: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Новый метод для порционной отправки
    private fun flushItemsToFirebase(items: List<MenuItem>, pointId: String) {
        val menuMap = mutableMapOf<String, Any>()
        items.forEach { item ->
            menuMap[item.id] = mapOf(
                "id" to item.id,
                "category" to item.category,
                "name" to item.name,
                "defaultValue" to item.defaultValue
            )
        }
        menusRef.child(pointId).updateChildren(menuMap)
    }
}