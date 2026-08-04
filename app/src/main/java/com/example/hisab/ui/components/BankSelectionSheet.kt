package com.example.hisab.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.hisab.data.db.entity.AccountEntity
import com.example.hisab.ui.theme.HisabTheme

data class BankOption(
    val name: String,
    val code: String
)

val ALL_INDIAN_BANKS = listOf(
    BankOption("State Bank of India (SBI)", "SBI"),
    BankOption("Bank of Baroda (BOB)", "BOB"),
    BankOption("HDFC Bank", "HDFC"),
    BankOption("ICICI Bank", "ICICI"),
    BankOption("Axis Bank", "AXIS"),
    BankOption("Punjab National Bank (PNB)", "PNB"),
    BankOption("Canara Bank", "CANARA"),
    BankOption("Kotak Mahindra Bank", "KOTAK"),
    BankOption("Union Bank of India", "UNION"),
    BankOption("Bank of India (BOI)", "BOI"),
    BankOption("Central Bank of India (CBI)", "CBI"),
    BankOption("Indian Bank", "IDIB"),
    BankOption("Indian Overseas Bank (IOB)", "IOB"),
    BankOption("Punjab & Sind Bank", "PSB"),
    BankOption("UCO Bank", "UCO"),
    BankOption("Bank of Maharashtra", "BOM"),
    BankOption("IDBI Bank", "IDBI"),
    BankOption("IndusInd Bank", "INDUS"),
    BankOption("Federal Bank", "FED"),
    BankOption("YES Bank", "YES"),
    BankOption("RBL Bank", "RBL"),
    BankOption("IDFC FIRST Bank", "IDFC"),
    BankOption("Bandhan Bank", "BANDHAN"),
    BankOption("South Indian Bank", "SIB"),
    BankOption("Karur Vysya Bank", "KVB"),
    BankOption("Jammu & Kashmir Bank", "JKB"),
    BankOption("City Union Bank", "CUB"),
    BankOption("Tamilnad Mercantile Bank", "TMB"),
    BankOption("AU Small Finance Bank", "AU"),
    BankOption("Equitas Small Finance Bank", "EQUITAS"),
    BankOption("Ujjivan Small Finance Bank", "UJJIVAN"),
    BankOption("Capital Small Finance Bank", "CAPITAL"),
    BankOption("Fincare Small Finance Bank", "FINCARE"),
    BankOption("Jana Small Finance Bank", "JANA"),
    BankOption("Suryoday Small Finance Bank", "SURYODAY"),
    BankOption("Utkarsh Small Finance Bank", "UTKARSH"),
    BankOption("ESAF Small Finance Bank", "ESAF"),
    BankOption("Paytm Payments Bank", "PYTM"),
    BankOption("Airtel Payments Bank", "AIRTEL"),
    BankOption("India Post Payments Bank", "IPPB"),
    BankOption("Jio Payments Bank", "JIO"),
    BankOption("NSDL Payments Bank", "NSDL"),
    BankOption("Fi Money (Federal Bank)", "FI"),
    BankOption("Jupiter Money (Federal Bank)", "JUPITER"),
    BankOption("Slice", "SLICE"),
    BankOption("Niyo Global / NiyoX", "NIYO")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankSelectionSheet(
    account: AccountEntity,
    onDismiss: () -> Unit,
    onSaveBankMapping: (AccountEntity, String, String?) -> Unit
) {
    val context = LocalContext.current
    val colors = HisabTheme.colors
    var selectedBank by remember { mutableStateOf(account.bankCode ?: "BOB") }
    var accountLast4 by remember { mutableStateOf(account.accountLast4 ?: "") }
    var searchQuery by remember { mutableStateOf("") }

    val filteredBanks = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            ALL_INDIAN_BANKS
        } else {
            ALL_INDIAN_BANKS.filter {
                it.name.contains(searchQuery, ignoreCase = true) || it.code.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Permissions handled
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.AccountBalance,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Link Bank for ${account.name}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Enables automatic SMS payment detection & 1-tap logging",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Privacy Banner
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "100% Privacy Guaranteed: Your bank SMS messages are processed locally on your device. Zero data ever leaves your phone.",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = accountLast4,
                onValueChange = { if (it.length <= 4 && it.all { char -> char.isDigit() }) accountLast4 = it },
                label = { Text("Account Last 4 Digits (Optional)") },
                placeholder = { Text("e.g. 1234") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = colors.cardBorder
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search Bank (Name or Code)") },
                placeholder = { Text("e.g. SBI, BOB, HDFC, Federal, Axis...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = colors.cardBorder
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Select Bank Provider (${filteredBanks.size} available)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.height(200.dp)
            ) {
                items(filteredBanks) { bank ->
                    val isSelected = selectedBank == bank.code
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface)
                            .clickable { selectedBank = bank.code }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = bank.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else colors.textPrimary
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    // Check & request permissions simultaneously
                    val permsToRequest = mutableListOf<String>()
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
                        permsToRequest.add(Manifest.permission.RECEIVE_SMS)
                    }
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
                        permsToRequest.add(Manifest.permission.READ_SMS)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            permsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }

                    if (permsToRequest.isNotEmpty()) {
                        permissionLauncher.launch(permsToRequest.toTypedArray())
                    }

                    onSaveBankMapping(account, selectedBank, accountLast4.ifEmpty { null })
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Filled.Sms, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save & Enable SMS Auto-Detect", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
