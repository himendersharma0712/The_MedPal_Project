import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.medpal.ChatMessageDao
import com.example.medpal.ChatMessageEntity


@Database(entities = [ChatMessageEntity::class], version = 1)
abstract class AppDatabase:RoomDatabase(){
    abstract fun chatMessageDao(): ChatMessageDao

    companion object{
        @Volatile
        private var INSTANCE:AppDatabase? = null

        fun getDatabase(context: Context):AppDatabase{
            return INSTANCE?: synchronized(this){
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "iris_chat_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}