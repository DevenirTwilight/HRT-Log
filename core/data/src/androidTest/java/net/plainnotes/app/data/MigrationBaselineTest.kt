package net.plainnotes.app.data
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
/** v1 baseline; add explicit migrations and runMigrationsAndValidate when v2 exists. */
class MigrationBaselineTest {
    @get:Rule val helper=MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),NotesDatabase::class.java)
    @Test fun exportedV1CanBeCreated(){helper.createDatabase("migration-baseline",1).use{db->SchemaGuards.install(db)}}
}
