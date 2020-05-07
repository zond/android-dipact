package se.oort.dipact

import java.util.*

class DiplicityJSON {
    class Message {
        val ID: String? = null
        val GameID: String? = null
        val ChannelMembers: List<String>? = null
        val Sender: String? = null
        val Body: String? = null
        val CreatedAt: Date? = null
    }

    class PhaseMeta {
        val PhaseOrdinal: Long? = null
        val Season: String? = null
        val Year: Long? = null
        val Type: String? = null
        val Resolved: Boolean? = null
        val CreatedAt: Date? = null
        val ResolvedAt: Date? = null
        val DeadlineAt: Date? = null
        val UnitsJSON: String? = null
        val SCsJSON: String? = null
    }

    val type: String? = null
    val gameID: String? = null
    val gameDesc: String? = null
    val phaseMeta: PhaseMeta? = null
    val message: Message? = null
    val clickAction: String? = null
}

