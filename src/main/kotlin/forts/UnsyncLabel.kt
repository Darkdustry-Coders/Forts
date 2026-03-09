package forts

import arc.func.Func
import arc.struct.Seq
import arc.util.io.Writes
import mindustry.gen.Player
import mindustry.gen.RemoveWorldLabelCallPacket
import mindustry.gen.WorldLabel
import mindustry.io.TypeIO
import java.lang.ref.WeakReference

class UnsyncLabel: WorldLabel() {
    private val syncWith = Seq<WeakReference<Player>>()
    var textGen: Func<Player, String>? = null
    private var currentPlayer: Player? = null

    override fun isSyncHidden(player: Player): Boolean {
        currentPlayer = player
        syncWith.remove { it.get() == null }
        return syncWith.remove { it.get() === player }
    }

    fun syncFor(player: Player) {
        syncWith.addUnique(WeakReference(player))
    }

    fun removeFor(player: Player) {
        val packet = RemoveWorldLabelCallPacket()
        packet.id = id
        player.con.send(packet, true)
    }

    override fun write(write: Writes) {
        write.s(0);
        write.b(this.flags.toInt());
        write.f(this.fontSize);
        TypeIO.writeString(write, textGen?.let { it[currentPlayer!!] } ?: this.text);
        currentPlayer = null
        write.f(this.x);
        write.f(this.y);
        write.f(this.z);
    }

    override fun writeSync(write: Writes) {
        write.b(this.flags.toInt());
        write.f(this.fontSize);
        TypeIO.writeString(write, textGen?.let { it[currentPlayer!!] } ?: this.text);
        currentPlayer = null
        write.f(this.x);
        write.f(this.y);
        write.f(this.z);
    }
}