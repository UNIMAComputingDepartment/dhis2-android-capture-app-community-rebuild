package org.dhis2.community.ui.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material3.MaterialTheme
import org.dhis2.community.ui.components.CollapsibleListButton


@Preview(showBackground = true)
@Composable
fun CollapsibleButtonPreview(){
    val context = LocalContext.current
    val sampleItems = List(9){"item ${it + 1}"}

    MaterialTheme{
        CollapsibleListButton(
            tittle = "Households",
            items = sampleItems,
            onItemClick = {//item ->
                //Toast.makeText(context, "Clicked : $item", Toast.LENGTH_SHORT).show()
            }
        )
    }
}