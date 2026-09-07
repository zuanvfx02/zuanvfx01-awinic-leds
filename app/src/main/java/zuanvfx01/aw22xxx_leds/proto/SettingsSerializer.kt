package zuanvfx01.aw22xxx_leds.proto

import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import zuanvfx01.aw22xxx_leds.Settings
import java.io.InputStream
import java.io.OutputStream

object SettingsSerializer : Serializer<Settings> {
    override val defaultValue: Settings = Settings.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): Settings =
        try {
            Settings.parseFrom(input)
        } catch (_: InvalidProtocolBufferException) {
            defaultValue
        }

    override suspend fun writeTo(t: Settings, output: OutputStream) = t.writeTo(output)
}
