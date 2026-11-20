package net.cardolan.pbill

import android.app.Application
import net.cardolan.pbill.data.PBillDatabase
import net.cardolan.pbill.data.Repository
import net.cardolan.pbill.data.Settings

class PBillApplication : Application() {
    val repository: Repository by lazy { Repository(PBillDatabase.build(this).dao()) }
    val settings: Settings by lazy { Settings(this) }
}
