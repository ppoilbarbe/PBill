package net.cardolan.pbill.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import net.cardolan.pbill.R
import net.cardolan.pbill.domain.ExpenseCategory

@get:StringRes
val ExpenseCategory.labelRes: Int
    get() = when (this) {
        ExpenseCategory.NONE -> R.string.category_none
        ExpenseCategory.GROCERIES -> R.string.category_groceries
        ExpenseCategory.RESTAURANT -> R.string.category_restaurant
        ExpenseCategory.BAR -> R.string.category_bar
        ExpenseCategory.ACCOMMODATION -> R.string.category_accommodation
        ExpenseCategory.CAR -> R.string.category_car
        ExpenseCategory.FUEL -> R.string.category_fuel
        ExpenseCategory.TOLLS -> R.string.category_tolls
        ExpenseCategory.TRAVEL -> R.string.category_travel
        ExpenseCategory.LEISURE -> R.string.category_leisure
        ExpenseCategory.HOUSING -> R.string.category_housing
        ExpenseCategory.BILLS -> R.string.category_bills
        ExpenseCategory.HEALTH -> R.string.category_health
        ExpenseCategory.GIFTS -> R.string.category_gifts
    }

/** Icon drawable of a category; null for [ExpenseCategory.NONE], shown with the shopping cart. */
@get:DrawableRes
private val ExpenseCategory.iconRes: Int?
    get() = when (this) {
        ExpenseCategory.NONE -> null
        ExpenseCategory.GROCERIES -> R.drawable.ic_category_groceries
        ExpenseCategory.RESTAURANT -> R.drawable.ic_category_restaurant
        ExpenseCategory.BAR -> R.drawable.ic_category_bar
        ExpenseCategory.ACCOMMODATION -> R.drawable.ic_category_accommodation
        ExpenseCategory.CAR -> R.drawable.ic_category_car
        ExpenseCategory.FUEL -> R.drawable.ic_category_fuel
        ExpenseCategory.TOLLS -> R.drawable.ic_category_tolls
        ExpenseCategory.TRAVEL -> R.drawable.ic_category_travel
        ExpenseCategory.LEISURE -> R.drawable.ic_category_leisure
        ExpenseCategory.HOUSING -> R.drawable.ic_category_housing
        ExpenseCategory.BILLS -> R.drawable.ic_category_bills
        ExpenseCategory.HEALTH -> R.drawable.ic_category_health
        ExpenseCategory.GIFTS -> R.drawable.ic_category_gifts
    }

@Composable
fun categoryPainter(category: ExpenseCategory): Painter =
    category.iconRes?.let { painterResource(it) } ?: rememberVectorPainter(Icons.Default.ShoppingCart)

/** Category picker showing the icon and the name of each category. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDropdown(
    label: String,
    selected: ExpenseCategory,
    onSelect: (ExpenseCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = stringResource(selected.labelRes),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            leadingIcon = { Icon(categoryPainter(selected), contentDescription = null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ExpenseCategory.entries.forEach { category ->
                DropdownMenuItem(
                    text = { Text(stringResource(category.labelRes)) },
                    leadingIcon = { Icon(categoryPainter(category), contentDescription = null) },
                    onClick = {
                        onSelect(category)
                        expanded = false
                    },
                )
            }
        }
    }
}
