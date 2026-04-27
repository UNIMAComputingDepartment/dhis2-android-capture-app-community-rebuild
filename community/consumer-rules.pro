# Keep community datastore configuration DTOs used via Gson reflection.
-keep class org.dhis2.community.tasking.models.TaskingConfig { *; }
-keep class org.dhis2.community.tasking.models.TaskingConfig$* { *; }

-keep class org.dhis2.community.workflow.WorkflowConfig { *; }
-keep class org.dhis2.community.workflow.EntityAutoCreationConfig { *; }
-keep class org.dhis2.community.workflow.AttributeMapping { *; }
-keep class org.dhis2.community.workflow.AutoIncrementAttributes { *; }
-keep class org.dhis2.community.workflow.ProgramEnrollmentControl { *; }

-keep class org.dhis2.community.relationships.RelationshipConfig { *; }
-keep class org.dhis2.community.relationships.Relationship { *; }
-keep class org.dhis2.community.relationships.Access { *; }
-keep class org.dhis2.community.relationships.View { *; }
-keep class org.dhis2.community.relationships.RelatedProgram { *; }
-keep class org.dhis2.community.relationships.AttributeMapping { *; }

-keep class org.dhis2.community.medicalHistory.MedicalHistoryConfig { *; }
-keep class org.dhis2.community.medicalHistory.MedicalHistoryConfig$* { *; }

