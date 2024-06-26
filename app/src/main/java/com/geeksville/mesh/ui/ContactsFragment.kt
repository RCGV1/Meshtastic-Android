package com.geeksville.mesh.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.R
import com.geeksville.mesh.model.Contact
import com.geeksville.mesh.model.ContactsViewModel
import com.geeksville.mesh.ui.theme.AppTheme
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class ContactsFragment : ScreenFragment("Messages"), Logging {

    private val actionModeCallback: ActionModeCallback = ActionModeCallback()
    private var actionMode: ActionMode? = null
    private val model: ContactsViewModel by viewModels()

    private val contacts get() = model.contactList.value
    private val selectedList get() = model.selectedContacts.value.toList()

    private val selectedContacts get() = contacts.filter { it.contactKey in selectedList }
    private val isAllMuted get() = selectedContacts.all { it.isMuted }
    private val selectedCount get() = selectedContacts.sumOf { it.messageCount }

    private fun onClick(contact: Contact) {
        if (actionMode != null) {
            onLongClick(contact)
        } else {
            debug("calling MessagesFragment filter:${contact.contactKey}")
            parentFragmentManager.navigateToMessages(contact.contactKey, contact.longName)
        }
    }

    private fun onLongClick(contact: Contact) {
        if (actionMode == null) {
            actionMode = (activity as AppCompatActivity).startSupportActionMode(actionModeCallback)
        }

        val selected = model.updateSelectedContacts(contact.contactKey)
        if (selected.isEmpty()) {
            // finish action mode when no items selected
            actionMode?.finish()
        } else {
            // show total items selected on action mode title
            actionMode?.title = selected.size.toString()
        }
    }

    override fun onPause() {
        actionMode?.finish()
        super.onPause()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AppTheme {
                    ContactsScreen(model, ::onClick, ::onLongClick)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        actionMode?.finish()
        actionMode = null
    }

    private inner class ActionModeCallback : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.menu_messages, menu)
            menu.findItem(R.id.resendButton).isVisible = false
            mode.title = "1"
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            menu.findItem(R.id.muteButton).setIcon(
                if (isAllMuted) {
                    R.drawable.ic_twotone_volume_up_24
                } else {
                    R.drawable.ic_twotone_volume_off_24
                }
            )
            return false
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            when (item.itemId) {
                R.id.muteButton -> if (isAllMuted) {
                    model.setMuteUntil(selectedList, 0L)
                    mode.finish()
                } else {
                    var muteUntil: Long = Long.MAX_VALUE
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.mute_notifications)
                        .setSingleChoiceItems(
                            setOf(
                                R.string.mute_8_hours,
                                R.string.mute_1_week,
                                R.string.mute_always,
                            ).map(::getString).toTypedArray(),
                            2
                        ) { _, which ->
                            muteUntil = when (which) {
                                0 -> System.currentTimeMillis() + TimeUnit.HOURS.toMillis(8)
                                1 -> System.currentTimeMillis() + TimeUnit.DAYS.toMillis(7)
                                else -> Long.MAX_VALUE // always
                            }
                        }
                        .setPositiveButton(getString(R.string.okay)) { _, _ ->
                            debug("User clicked muteButton")
                            model.setMuteUntil(selectedList, muteUntil)
                            mode.finish()
                        }
                        .setNeutralButton(R.string.cancel) { _, _ ->
                        }
                        .show()
                }

                R.id.deleteButton -> {
                    val deleteMessagesString = resources.getQuantityString(
                        R.plurals.delete_messages,
                        selectedCount,
                        selectedCount
                    )
                    MaterialAlertDialogBuilder(requireContext())
                        .setMessage(deleteMessagesString)
                        .setPositiveButton(getString(R.string.delete)) { _, _ ->
                            debug("User clicked deleteButton")
                            model.deleteContacts(selectedList)
                            mode.finish()
                        }
                        .setNeutralButton(R.string.cancel) { _, _ ->
                        }
                        .show()
                }
                R.id.selectAllButton -> {
                    // if all selected -> unselect all
                    if (selectedList.size == contacts.size) {
                        model.clearSelectedContacts()
                        mode.finish()
                    } else {
                        // else --> select all
                        model.clearSelectedContacts()
                        contacts.forEach {
                            model.updateSelectedContacts(it.contactKey)
                        }
                        actionMode?.title = contacts.size.toString()
                    }
                }
            }
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            model.clearSelectedContacts()
            actionMode = null
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactsScreen(
    model: ContactsViewModel = hiltViewModel(),
    onClick: (Contact) -> Unit,
    onLongClick: (Contact) -> Unit,
) {
    val contacts by model.contactList.collectAsStateWithLifecycle(emptyList())
    val selectedKeys by model.selectedContacts.collectAsStateWithLifecycle()
    // val inSelectionMode by remember { derivedStateOf { selectedContacts.isNotEmpty() } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(6.dp),
    ) {
        items(contacts, key = { it.contactKey }) { contact ->
            val selected = selectedKeys.contains(contact.contactKey)
            ContactItem(
                contact = contact,
                modifier = Modifier
                    .background(color = if (selected) Color.Gray else MaterialTheme.colors.background)
                    .combinedClickable(
                        onClick = { onClick(contact) },
                        onLongClick = { onLongClick(contact) },
                    )
            )
        }
    }
}
