package org.dhis2.community.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import org.dhis2.community.R

@Composable
fun CollapsibleListButton(
    tittle: String,
    items: List<String>,
    onItemClick: (String) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val filteredItems = items.filter {
        it.contains(searchQuery, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .padding(10.dp)
            .fillMaxWidth()
            .background(Color(0xFFE0E0E0), shape = RoundedCornerShape(8.dp))
    ) {
        // Header row
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
                            //.fillMaxWidth()
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
                filteredItems.forEachIndexed { index, item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .heightIn(max = 40.dp)
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
                                //.fillMaxWidth()
                        ) {
                            Text(
                                text = "Ainafe Enock Chisati",
                                fontSize = 14.sp,
                                lineHeight = 7.sp,

                                modifier = Modifier
                                    //.fillMaxWidth()
                                    //.fillMaxHeight()
                                    //.clickable { onItemClick(item) }
                                    .padding(vertical = 0.dp)
                            )

                            Text(
                                text = "Female",
                                fontSize = 12.sp,
                                lineHeight = 7.sp,

                                modifier = Modifier
                                //.padding(start = 15.dp)
                                //.fillMaxWidth()
                                //.clickable { onItemClick(item) }
                                //.padding(vertical = 8.dp)
                            )

                        }
                        //Spacer(modifier = Modifier.width(10.dp))
                        //Column {
                            Text(
                            text = "20 Years",
                            fontSize = 9.sp,
                            lineHeight = 7.sp,

                            modifier = Modifier
                                .fillMaxWidth()
                                //.windowInsetsTopHeight()

                            //.clickable { onItemClick(item) }
                                .padding(top = 0.dp)
                        )
                        //}



                        //Spacer(modifier = Modifier.height(6.dp))

                    }
                    

                }
            }
        }
    }
}


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