package org.dhis2.community.relationships.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.dhis2.community.R
import org.dhis2.community.relationships.CmtRelationshipViewModel

@Composable
fun CollapsibleListButton(
    tittle: String,
    items: List<CmtRelationshipViewModel>,
    onItemClick: (CmtRelationshipViewModel) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredItems = items.filter {
        it.primaryAttribute.contains(searchQuery, ignoreCase = true)

    }

    Column(
        modifier = Modifier
            .padding(10.dp)
            .fillMaxWidth()
            .background(Color(0xFFE0E0E0), shape = RoundedCornerShape(8.dp))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(0.dp)
        ) {
            Spacer(modifier = Modifier.width(5.dp))
            Icon(
                painter = painterResource(R.drawable.ic_people_outline),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = tittle,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            )
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                modifier = Modifier.size(20.dp),
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(10.dp))
            IconButton(onClick = { }) {
                Icon(
                    Icons.Default.Add,
                    modifier = Modifier.size(20.dp),
                    contentDescription = "Add Household"
                )
            }
        }

        if (expanded) {
            if (items.size > 10) {
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .fillMaxWidth()
                        .width(15.dp)
                        .border(1.dp, Color.Gray, shape = RoundedCornerShape(4.dp))
                        .padding(horizontal = 20.dp, vertical = 5.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Search households...",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }

                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 12.sp, color = Color.Black),
                        modifier = Modifier
                            .padding(horizontal = 20.dp, vertical = 5.dp),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp)
            ) {
                filteredItems.forEach { item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .height(40.dp)
                            //.heightIn(max = 40.dp, min = 40.dp)
                            .clickable { onItemClick(item) }

                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_people_outline),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))

                        Column(
                            modifier = Modifier
                                .padding(4.dp)
                        ) {
                            Text(
                                text = item.primaryAttribute,
                                fontSize = 14.sp,
                                lineHeight = 7.sp,

                                modifier = Modifier
                                    .padding(vertical = 0.dp)

                            )

                            item.secondaryAttribute?.let {
                                Text(
                                    text = it,
                                    fontSize = 10.sp,
                                    lineHeight = 7.sp,

                                    modifier = Modifier
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))

                        item.tertiaryAttribute?.let {
                            Text(
                                text = it,
                                fontSize = 12.sp,
                                lineHeight = 10.sp,

                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .align(Alignment.Top)
                                    //.weight(1f),
                                )
                        }

                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            painter = painterResource(R.drawable.ic_navigate_next),
                            contentDescription = null,
                            modifier = Modifier
                                .size(20.dp)
                                //.weight(1f)
                                .padding(end = 7.dp)
                                .fillMaxWidth()
                        )
                    }
                }

            }
        }
    }
}


/*
@Preview(showBackground = true)
@Composable
fun CollapsibleButtonPreview() {
    val context = LocalContext.current
    val sampleItems = List(9) { "house#${it + 1}" }


    MaterialTheme {
        CollapsibleListButton(
            tittle = "Households",
            items = sampleItems,
            onItemClick = {//item ->
                //Toast.makeText(context, "Clicked : $item", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

 */