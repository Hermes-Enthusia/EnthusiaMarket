package net.badgersmc.em.infrastructure.persistence

import net.badgersmc.em.domain.stall.*
import net.badgersmc.nexus.annotations.Repository
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

@Repository
class StallRepositorySql(private val ds: DataSource) : StallRepository {

    override fun findById(id: StallId): Stall? = queryOne("SELECT * FROM stalls WHERE id = ?") {
        setString(1, id.value)
    }

    override fun findByRegion(world: String, regionId: String): Stall? =
        queryOne("SELECT * FROM stalls WHERE world = ? AND region_id = ?") {
            setString(1, world); setString(2, regionId)
        }

    override fun all(): List<Stall> = queryMany("SELECT * FROM stalls") {}

    override fun byState(state: StallState): List<Stall> =
        queryMany("SELECT * FROM stalls WHERE state = ?") { setString(1, state.name) }

    override fun create(stall: Stall) {
        ds.connection.use { conn ->
            conn.prepareStatement(
                """INSERT INTO stalls
                   (id, region_id, world, state, owner_type, owner_id, owner_since,
                    winning_bid, rent_mode, rent_pct, rent_flat, members, max_members, next_rent_at,
                    extra_entities, extra_total)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"""
            ).use { ps ->
                bind(ps, stall)
                ps.executeUpdate()
            }
        }
    }

    override fun save(stall: Stall) {
        ds.connection.use { conn ->
            conn.prepareStatement(
                """UPDATE stalls SET
                     region_id = ?, world = ?, state = ?, owner_type = ?, owner_id = ?,
                     owner_since = ?, winning_bid = ?, rent_mode = ?, rent_pct = ?, rent_flat = ?,
                     members = ?, max_members = ?, next_rent_at = ?,
                     extra_entities = ?, extra_total = ?
                   WHERE id = ?"""
            ).use { ps ->
                ps.setString(1, stall.regionId)
                ps.setString(2, stall.world)
                ps.setString(3, stall.state.name)
                ps.setString(4, stall.owner.type.name)
                ps.setString(5, stall.owner.id)
                if (stall.ownerSince != null) ps.setLong(6, stall.ownerSince.toEpochMilli())
                else ps.setNull(6, java.sql.Types.INTEGER)
                ps.setLong(7, stall.winningBid)
                ps.setString(8, stall.rentTerms.mode.name)
                ps.setDouble(9, stall.rentTerms.pct)
                ps.setLong(10, stall.rentTerms.flatAmount)
                ps.setString(11, encodeMembers(stall.members))
                ps.setInt(12, stall.maxMembers)
                if (stall.nextRentAt != null) ps.setLong(13, stall.nextRentAt.toEpochMilli())
                else ps.setNull(13, java.sql.Types.INTEGER)
                ps.setString(14, encodeIntMap(stall.extraEntities))
                ps.setInt(15, stall.extraTotal)
                ps.setString(16, stall.id.value)
                ps.executeUpdate()
            }
        }
    }

    private fun bind(ps: PreparedStatement, stall: Stall) {
        ps.setString(1, stall.id.value)
        ps.setString(2, stall.regionId)
        ps.setString(3, stall.world)
        ps.setString(4, stall.state.name)
        ps.setString(5, stall.owner.type.name)
        ps.setString(6, stall.owner.id)
        if (stall.ownerSince != null) ps.setLong(7, stall.ownerSince.toEpochMilli())
        else ps.setNull(7, java.sql.Types.INTEGER)
        ps.setLong(8, stall.winningBid)
        ps.setString(9, stall.rentTerms.mode.name)
        ps.setDouble(10, stall.rentTerms.pct)
        ps.setLong(11, stall.rentTerms.flatAmount)
        ps.setString(12, encodeMembers(stall.members))
        ps.setInt(13, stall.maxMembers)
        if (stall.nextRentAt != null) ps.setLong(14, stall.nextRentAt.toEpochMilli())
        else ps.setNull(14, java.sql.Types.INTEGER)
        ps.setString(15, encodeIntMap(stall.extraEntities))
        ps.setInt(16, stall.extraTotal)
    }

    private fun encodeMembers(members: Set<UUID>): String =
        members.joinToString(",") { it.toString() }

    private fun decodeMembers(raw: String?): Set<UUID> {
        if (raw.isNullOrEmpty()) return emptySet()
        return raw.split(',')
            .asSequence()
            .filter { it.isNotBlank() }
            .map { UUID.fromString(it.trim()) }
            .toSet()
    }

    /**
     * Encode a [Map<String, Int>] as a minimal JSON object string, e.g.
     * `{"VILLAGER":5,"ZOMBIE":2}`. Keys and values are guaranteed to contain
     * no characters that need escaping (EntityType names are ASCII identifiers;
     * values are plain integers), so manual encoding is safe here.
     */
    private fun encodeIntMap(map: Map<String, Int>): String {
        if (map.isEmpty()) return "{}"
        return map.entries.joinToString(",", prefix = "{", postfix = "}") { (k, v) ->
            "\"$k\":$v"
        }
    }

    /**
     * Decode a JSON object produced by [encodeIntMap] back into a [Map<String, Int>].
     * Uses a simple regex that matches `"KEY":VALUE` pairs.
     */
    private fun decodeIntMap(raw: String?): Map<String, Int> {
        if (raw.isNullOrBlank() || raw.trim() == "{}") return emptyMap()
        val result = mutableMapOf<String, Int>()
        val entryPattern = Regex(""""([^"]+)"\s*:\s*(-?\d+)""")
        for (match in entryPattern.findAll(raw)) {
            val key = match.groupValues[1]
            val value = match.groupValues[2].toIntOrNull() ?: continue
            result[key] = value
        }
        return result
    }

    private fun queryOne(sql: String, prep: PreparedStatement.() -> Unit): Stall? {
        ds.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.prep()
                ps.executeQuery().use { rs ->
                    return if (rs.next()) mapRow(rs) else null
                }
            }
        }
    }

    private fun queryMany(sql: String, prep: PreparedStatement.() -> Unit): List<Stall> {
        ds.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.prep()
                ps.executeQuery().use { rs ->
                    val out = mutableListOf<Stall>()
                    while (rs.next()) out.add(mapRow(rs))
                    return out
                }
            }
        }
    }

    private fun mapRow(rs: ResultSet): Stall {
        val ownerType = OwnerType.valueOf(rs.getString("owner_type"))
        val ownerId = rs.getString("owner_id")
        val owner = if (ownerType == OwnerType.NONE) OwnerRef.unowned() else OwnerRef(ownerType, ownerId)
        val ownerSinceMs = rs.getLong("owner_since").takeIf { !rs.wasNull() }
        val rentMode = RentTerms.Mode.valueOf(rs.getString("rent_mode"))
        val rentTerms = RentTerms(rentMode, rs.getDouble("rent_pct"), rs.getLong("rent_flat"))
        return Stall(
            id = StallId(rs.getString("id")),
            regionId = rs.getString("region_id"),
            world = rs.getString("world"),
            state = StallState.valueOf(rs.getString("state")),
            owner = owner,
            ownerSince = ownerSinceMs?.let { Instant.ofEpochMilli(it) },
            winningBid = rs.getLong("winning_bid"),
            rentTerms = rentTerms,
            members = decodeMembers(rs.getString("members")),
            maxMembers = rs.getInt("max_members"),
            nextRentAt = rs.getLong("next_rent_at").takeIf { !rs.wasNull() }?.let { Instant.ofEpochMilli(it) },
            extraEntities = decodeIntMap(rs.getString("extra_entities")),
            extraTotal = rs.getInt("extra_total"),
        )
    }
}
