package com.jason.dnsrouter

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import java.text.SimpleDateFormat
import java.util.*

class DnsActivity : AppCompatActivity() {
    private lateinit var stats: DnsStats
    private lateinit var listView: ListView
    private lateinit var statsContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        stats = DnsStats(this)
        
        val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(if (isDark) "#121212".toColorInt() else Color.WHITE)
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 32)
        }

        val header = TextView(this).apply {
            text = "DNS Activity Log"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            setTextColor(if (isDark) Color.WHITE else Color.BLACK)
        }
        headerRow.addView(header)

        headerRow.addView(ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(if (isDark) Color.WHITE else Color.BLACK)
            setOnClickListener { finish() }
        })
        root.addView(headerRow)

        statsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 32)
            gravity = Gravity.CENTER
        }
        root.addView(statsContainer)

        listView = ListView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
            divider = null
        }
        root.addView(listView)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, 32, 0, 0)
        }
        
        btnRow.addView(Button(this).apply {
            text = "Clear Log"
            setOnClickListener {
                stats.clear()
                refresh()
            }
        })

        btnRow.addView(Button(this).apply {
            text = "Refresh"
            setOnClickListener { refresh() }
        })
        
        root.addView(btnRow)
        setContentView(root)
        refresh()
    }

    private fun refresh() {
        statsContainer.removeAllViews()
        val s = stats
        addStat("Queries", s.get("queries").toString())
        addStat("Allowed", s.get("responses").toString())
        addStat("Blocked", s.get("nxdomain").toString())
        addStat("Failed", s.get("errors").toString())

        val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val log = stats.getQueryLog()
        val adapter = object : ArrayAdapter<DnsStats.QueryRecord>(this, android.R.layout.simple_list_item_2, log) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val text1 = view.findViewById<TextView>(android.R.id.text1)
                val text2 = view.findViewById<TextView>(android.R.id.text2)
                val item = getItem(position)!!
                
                text1.text = item.domain
                text1.setTextColor(if (isDark) Color.WHITE else Color.BLACK)
                
                val color = when (item.status) {
                    "allowed" -> "#4CAF50".toColorInt() // Green
                    "blocked" -> "#F44336".toColorInt() // Red
                    "denied" -> "#FF5722".toColorInt()  // Orange/Amber Red
                    "failed" -> "#FFB300".toColorInt()  // Construction Yellow
                    "default_allowed" -> "#2196F3".toColorInt() // Blue
                    "excluded" -> if (isDark) Color.WHITE else Color.DKGRAY
                    else -> Color.GRAY
                }
                val symbol = when (item.status) {
                    "allowed" -> "✓"
                    "blocked" -> "🛡"
                    "denied" -> "🚫"
                    "failed" -> "⚠"
                    "default_allowed" -> "→"
                    "excluded" -> "↪"
                    else -> "•"
                }
                
                val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(item.timestamp))
                val displayStatus = when(item.status) {
                    "default_allowed" -> "DEFAULT ALLOWED"
                    "excluded" -> "EXCLUDED"
                    else -> item.status.uppercase()
                }
                text2.text = "$symbol $time - $displayStatus"
                text2.setTextColor(color)
                text2.typeface = Typeface.DEFAULT_BOLD
                return view
            }
        }
        listView.adapter = adapter
    }

    private fun addStat(label: String, value: String) {
        val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            gravity = Gravity.CENTER
        }
        container.addView(TextView(this).apply { 
            text = label
            textSize = 12f
            alpha = 0.7f
            setTextColor(if (isDark) Color.WHITE else Color.BLACK)
        })
        container.addView(TextView(this).apply { 
            text = value
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (isDark) Color.WHITE else Color.BLACK)
        })
        statsContainer.addView(container)
    }
}
