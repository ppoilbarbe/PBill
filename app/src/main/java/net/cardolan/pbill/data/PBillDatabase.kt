package net.cardolan.pbill.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [GroupEntity::class, MemberEntity::class, ExpenseEntity::class, ShareEntity::class],
    version = 1,
)
abstract class PBillDatabase : RoomDatabase() {
    abstract fun dao(): PBillDao

    companion object {
        fun build(context: Context): PBillDatabase =
            Room.databaseBuilder(context, PBillDatabase::class.java, "pbill.db").build()
    }
}
