package com.rafambn.kmpvpn


interface NATMode {

    val names: Set<String>

}

class SNAT(to: Set<String>) : NATMode {

    override val names: Set<String> = to

    fun addTo(out: String): SNAT {
        val s = LinkedHashSet(names)
        s.add(out)
        return SNAT(s)
    }

    override fun toString(): String {
        return "SNAT [to=$names]"
    }

    companion object {
        fun forNames(out: Set<String>): SNAT {
            return SNAT(LinkedHashSet(out))
        }

        fun forNames(vararg out: String): SNAT {
            return forNames(linkedSetOf(*out))
        }

        fun toIpv4Addresses(to: NetworkInterface): Collection<String> { //TODO(Implement this)
            val ipv4addrs = to.interfaceAddresses
                .asSequence()
                .map { it.address }
                .filter { it.isIpv4 }
                .map { "Random String" }
                .toList()
            check(ipv4addrs.isNotEmpty()) { "NAT is currently only supported for IPv4 networks." }
            return ipv4addrs
        }
    }
}

class MASQUERADE(out: Set<String>) : NATMode {

    override val names: Set<String> = out

    fun addOut(out: String): MASQUERADE {
        val s = LinkedHashSet(names)
        s.add(out)
        return MASQUERADE(s)
    }

    override fun toString(): String {
        return "MASQUERADE [in=$names]"
    }

    companion object {
        fun forNames(vararg out: String): MASQUERADE {
            return forNames(linkedSetOf(*out))
        }

        fun forNames(out: Set<String>): MASQUERADE {
            return MASQUERADE(LinkedHashSet(out))
        }
    }
}
