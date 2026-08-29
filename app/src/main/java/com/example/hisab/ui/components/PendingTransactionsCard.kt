package com.example.hisab.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.hisab.data.db.entity.PendingTransactionEntity
import com.example.hisab.data.model.TransactionConfidence
import com.example.hisab.ui.theme.HisabTheme
import com.example.hisab.util.CurrencyFormatter

/** Amber, deliberately neither the credit green nor the debit red: this row is a *guess*. */
private val InferredAccent = Color(0xFFF59E0B)

/**
 * A pending row the app inferred from a balance discrepancy rather than read out of an SMS.
 *
 * `BALANCE_RECONCILIATION` is the only producer of [TransactionConfidence.INFERRED], so one check is
 * enough; keeping it to the confidence column means the UI asks "how sure are we?" rather than
 * "where did it come from?", which is the question the styling actually answers.
 */
private val PendingTransactionEntity.isInferred: Boolean
    get() = confidence == TransactionConfidence.INFERRED.name

@Composable
fun PendingTransactionsCard(
    pendingTransactions: List<PendingTransactionEntity>,
    onApprove: (PendingTransactionEntity, String) -> Unit,
    onDismiss: (PendingTransactionEntity) -> Unit,
    /**
     * Invoked for inferred rows instead of [onApprove]. An inferred row has no merchant, no category
     * and an amount that is only a *net* — there is nothing to one-tap approve, so the caller must
     * open an editor and let the user say what actually happened.
     */
    onReview: (PendingTransactionEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (pendingTransactions.isEmpty()) return

    val colors = HisabTheme.colors

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(colors.cardBorder))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Sms,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Pending Bank Transactions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Text(
                            // Not "N unlogged SMS transactions": an inferred row is neither read from
                            // an SMS nor necessarily one transaction. This counts rows awaiting the
                            // user, which is true of both kinds.
                            text = "${pendingTransactions.size} item${if (pendingTransactions.size > 1) "s" else ""} waiting for your confirmation",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            pendingTransactions.take(3).forEach { pending ->
                PendingItemRow(
                    pending = pending,
                    onApprove = { category -> onApprove(pending, category) },
                    onReview = { onReview(pending) },
                    onDismiss = { onDismiss(pending) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun PendingItemRow(
    pending: PendingTransactionEntity,
    onApprove: (String) -> Unit,
    onReview: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = HisabTheme.colors
    val isInferred = pending.isInferred
    val isCredit = pending.type == "CREDIT"
    val accentColor = when {
        isInferred -> InferredAccent
        isCredit -> Color(0xFF10B981)
        else -> Color(0xFFEF4444)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        // No +/- on an inferred amount. The figure is a *net* discrepancy between what
                        // Hisab expected and what the bank reported, so signing it would assert a
                        // direction the arithmetic cannot support.
                        text = when {
                            isInferred -> CurrencyFormatter.format(pending.amount)
                            isCredit -> "+${CurrencyFormatter.format(pending.amount)}"
                            else -> "-${CurrencyFormatter.format(pending.amount)}"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isInferred) "of unlogged activity" else pending.bankName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textSecondary
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                if (isInferred) {
                    InferredBadge(bankName = pending.bankName, accountLast4 = pending.accountLast4)
                } else {
                    Text(
                        text = pending.merchantOrPayee ?: pending.rawSmsBody.take(45),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textTertiary,
                        maxLines = 1
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isInferred) {
                    // No quick-approve: there is no merchant to name and no category to guess. The
                    // caller opens an editor so the user supplies the amount and category themselves —
                    // the old default of "Salary"/"Groceries & Utilities" filed an invented
                    // transaction under an invented category.
                    TextButton(
                        onClick = onReview,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = "Review",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = InferredAccent
                        )
                    }
                } else {
                    // Quick approve button
                    IconButton(
                        onClick = { onApprove(if (isCredit) "Salary" else "Groceries & Utilities") },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Log Transaction",
                            tint = Color(0xFF10B981)
                        )
                    }
                }

                // Dismiss button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Dismiss",
                        tint = colors.textTertiary
                    )
                }
            }
        }
    }
}

/**
 * Says out loud that this row is a deduction, not a record. Without it an inferred row is
 * indistinguishable from a bank-confirmed one, and the user can't tell which numbers to trust.
 */
@Composable
private fun InferredBadge(bankName: String, accountLast4: String?) {
    val colors = HisabTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(InferredAccent.copy(alpha = 0.15f))
                .padding(horizontal = 5.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                contentDescription = null,
                tint = InferredAccent,
                modifier = Modifier.size(11.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = "Inferred from balance",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = InferredAccent
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (accountLast4.isNullOrBlank()) bankName else "$bankName ••••$accountLast4",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textTertiary,
            maxLines = 1
        )
    }
}
