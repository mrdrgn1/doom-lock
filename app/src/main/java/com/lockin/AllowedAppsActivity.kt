package com.lockin

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast

/** Three slots for the apps that stay reachable while locked in. */
class AllowedAppsActivity : Activity() {

    private companion object {
        const val SLOT_REQUEST = 400
    }

    private val cards = intArrayOf(R.id.slot0, R.id.slot1, R.id.slot2)
    private val names = intArrayOf(R.id.slotName0, R.id.slotName1, R.id.slotName2)
    private val hints = intArrayOf(R.id.slotHint0, R.id.slotHint1, R.id.slotHint2)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_allowed)

        findViewById<View>(R.id.allowedBack).setOnClickListener { finish() }
        cards.forEachIndexed { index, id ->
            val card = findViewById<View>(id)
            card.setOnClickListener { pick(index) }
            card.setOnLongClickListener {
                AllowedApps.setSlot(this, index, null)
                render()
                true
            }
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        for (index in 0 until AllowedApps.SLOTS) {
            val value = AllowedApps.slot(this, index)
            val name = findViewById<TextView>(names[index])
            val hint = findViewById<TextView>(hints[index])
            if (value.isEmpty()) {
                name.text = "Empty"
                name.setTextColor(getColor(R.color.faint))
            } else {
                name.text = AllowedApps.label(this, value)
                name.setTextColor(getColor(R.color.text))
            }
            hint.text = if (value.isEmpty()) "Tap to choose" else "Tap to change · hold to clear"
        }
    }

    private fun pick(index: Int) {
        val inner = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val picker = Intent(Intent.ACTION_PICK_ACTIVITY).putExtra(Intent.EXTRA_INTENT, inner)
        val resolved = runCatching { picker.resolveActivity(packageManager) }.getOrNull()
        if (resolved == null) {
            pickFromList(index)
            return
        }
        runCatching { startActivityForResult(picker, SLOT_REQUEST + index) }
            .onFailure { pickFromList(index) }
    }

    /** Phones without the system app picker get a plain list of everything launchable. */
    private fun pickFromList(index: Int) {
        val pm = packageManager
        val found = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        )
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != packageName }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
        if (found.isEmpty()) {
            Toast.makeText(this, "No apps to choose from", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Allowed app")
            .setItems(found.map { it.second }.toTypedArray()) { _, which ->
                AllowedApps.setSlot(this, index, found[which].first)
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @Deprecated("Start activity for result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val index = requestCode - SLOT_REQUEST
        if (index !in 0 until AllowedApps.SLOTS) return
        val chosen = data?.component?.packageName
            ?: data?.resolveActivity(packageManager)?.packageName
            ?: return
        if (chosen == packageName) return
        AllowedApps.setSlot(this, index, chosen)
        render()
    }

    @Deprecated("Same as the back arrow")
    override fun onBackPressed() {
        finish()
    }
}
