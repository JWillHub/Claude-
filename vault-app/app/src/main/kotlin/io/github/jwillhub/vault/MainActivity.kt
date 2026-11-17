package io.github.jwillhub.vault

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import io.github.jwillhub.vault.core.Entry

/** A two-line list of logins, shared by the main screen and the autofill picker. */
class EntryListAdapter(private val context: Context) : BaseAdapter() {
    private var shown: List<Entry> = emptyList()

    fun show(entries: List<Entry>, query: String) {
        val q = query.trim().lowercase()
        shown = if (q.isEmpty()) entries else entries.filter {
            it.title.lowercase().contains(q) || it.username.lowercase().contains(q) || it.url.lowercase().contains(q)
        }
        notifyDataSetChanged()
    }

    override fun getCount() = shown.size
    override fun getItem(position: Int): Entry = shown[position]
    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val row = convertView ?: View.inflate(context, android.R.layout.simple_list_item_2, null)
        val entry = shown[position]
        row.findViewById<TextView>(android.R.id.text1)!!.text = entry.title.ifBlank { "Untitled" }
        row.findViewById<TextView>(android.R.id.text2)!!.text = entry.username.ifBlank { entry.url }
        return row
    }
}

fun Context.searchBox(onChange: (String) -> Unit): EditText =
    input("Search", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS).apply {
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = onChange(s?.toString().orEmpty())
        })
    }

class MainActivity : BaseActivity() {
    private lateinit var adapter: EntryListAdapter
    private lateinit var search: EditText
    private lateinit var empty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = EntryListAdapter(this)
        search = searchBox { refresh() }
        empty = body("No logins yet. Choose Add to store your first one.").apply {
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(48), dp(24), dp(24))
        }
        val list = ListView(this).apply {
            adapter = this@MainActivity.adapter
            setOnItemClickListener { _, _, position, _ ->
                val entry = this@MainActivity.adapter.getItem(position)
                startActivity(Intent(this@MainActivity, EntryActivity::class.java).putExtra(EntryActivity.EXTRA_ID, entry.id))
            }
        }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(LinearLayout(this).apply {
            setPadding(dp(16), dp(8), dp(16), 0)
            addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        })
        root.addView(empty)
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val all = Session.all()
        adapter.show(all, search.text.toString())
        empty.visibility = if (all.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "Add").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        menu.add(0, 2, 1, "Settings")
        menu.add(0, 3, 2, "Lock now")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            1 -> startActivity(Intent(this, EntryActivity::class.java))
            2 -> startActivity(Intent(this, SettingsActivity::class.java))
            3 -> {
                Session.lock()
                startActivity(Intent(this, UnlockActivity::class.java))
                finish()
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }
}
