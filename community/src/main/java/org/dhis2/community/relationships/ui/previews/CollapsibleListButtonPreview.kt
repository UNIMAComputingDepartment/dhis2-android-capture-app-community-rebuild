package org.dhis2.community.relationships.ui.previews

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import org.dhis2.community.relationships.CmtRelationshipViewModel
import org.dhis2.community.relationships.ui.components.CollapsibleListButton


@Preview(showBackground = true)
@Composable
fun CollapsibleButtonPreview(){
    val context = LocalContext.current
    val sampleItems = List(11){"item ${it + 1}"}

    /*val repo = remember { RelationshipRepository(D2Manager.getD2()) }
    val tieAttributes = remember { repo.getRelatedTeis(
        "",
        "",
        TODO()
    ) }

     */


    val sampleClass = listOf<CmtRelationshipViewModel>()
    /*CmtRelationshipViewModel(
        "Jahnics Kotlin Code",
        "Female",
        "70 Years",
        "12345",
        relationship.relatedProgram.programUid,
        d2.enrollmentModule()
            .enrollments()
            .byTrackedEntityInstance().eq(tei.uid()!!)
            .byProgram().eq(relationship.relatedProgram.programUid)
            .blockingGet().firstOrNull()?.uid() ?: ""
    ),
    CmtRelationshipViewModel(
        "Witman Router",
        "null",
        "60 Years",
        "12345",
        relationship.relatedProgram.programUid,
        d2.enrollmentModule()
            .enrollments()
            .byTrackedEntityInstance().eq(tei.uid()!!)
            .byProgram().eq(relationship.relatedProgram.programUid)
            .blockingGet().firstOrNull()?.uid() ?: ""
    ),
    CmtRelationshipViewModel(
        "Russian Network",
        "Male",
        null,
        "null",
        relationship.relatedProgram.programUid,
        d2.enrollmentModule()
            .enrollments()
            .byTrackedEntityInstance().eq(tei.uid()!!)
            .byProgram().eq(relationship.relatedProgram.programUid)
            .blockingGet().firstOrNull()?.uid() ?: ""
    ),
    CmtRelationshipViewModel(
        "Haroon Switch",
        null,
        null,
        "12345",
        relationship.relatedProgram.programUid,
        d2.enrollmentModule()
            .enrollments()
            .byTrackedEntityInstance().eq(tei.uid()!!)
            .byProgram().eq(relationship.relatedProgram.programUid)
            .blockingGet().firstOrNull()?.uid() ?: ""
    ),
    CmtRelationshipViewModel(
        "James Kotlin",
        "Mphongo",
        null,
        "12345",
        relationship.relatedProgram.programUid,
        d2.enrollmentModule()
            .enrollments()
            .byTrackedEntityInstance().eq(tei.uid()!!)
            .byProgram().eq(relationship.relatedProgram.programUid)
            .blockingGet().firstOrNull()?.uid() ?: ""
    ),
    CmtRelationshipViewModel(
        "Witman Router",
        "Male",
        "30 Years",
        "12345",
        relationship.relatedProgram.programUid,
        d2.enrollmentModule()
            .enrollments()
            .byTrackedEntityInstance().eq(tei.uid()!!)
            .byProgram().eq(relationship.relatedProgram.programUid)
            .blockingGet().firstOrNull()?.uid() ?: ""
    ),
    CmtRelationshipViewModel(
        "Russian Network",
        "Male",
        null,
        "null",
        relationship.relatedProgram.programUid,
        d2.enrollmentModule()
            .enrollments()
            .byTrackedEntityInstance().eq(tei.uid()!!)
            .byProgram().eq(relationship.relatedProgram.programUid)
            .blockingGet().firstOrNull()?.uid() ?: ""
    ),
    CmtRelationshipViewModel(
        "Haroon Switch",
        null,
        null,
        "12345",
        relationship.relatedProgram.programUid,
        d2.enrollmentModule()
            .enrollments()
            .byTrackedEntityInstance().eq(tei.uid()!!)
            .byProgram().eq(relationship.relatedProgram.programUid)
            .blockingGet().firstOrNull()?.uid() ?: ""
    ),
)*/

    MaterialTheme{
        CollapsibleListButton(
            tittle = "Households",
            items = sampleClass,
            onItemClick = {//item ->
                //Toast.makeText(context, "Clicked : $item", Toast.LENGTH_SHORT).show()
            }
        )
    }
}