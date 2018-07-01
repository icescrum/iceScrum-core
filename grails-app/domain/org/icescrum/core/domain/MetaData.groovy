package org.icescrum.core.domain

class MetaData implements Serializable, Comparable {

    String metaKey
    String metaValue

    Long parentRef
    String parentType

    Date dateCreated
    Date lastUpdated

    static constraints = {
        metaKey blank: false, shared: 'keyMaxSize'
        metaValue nullable: true
        parentType blank: false, shared: 'keyMaxSize'
    }

    static mapping = {
        cache true
        table 'is_metadata'
        parentRef index: 'metadata_parent_ref_index'
        parentType index: 'metadata_parent_type_index'
        metaKey index: 'metadata_key_index'
    }

    @Override
    int compareTo(Object o) {
        return parentType.compareTo(o.parentType) ?:
               parentRef.compareTo(o.parentRef) ?:
               dateCreated.compareTo(o.dateCreated) ?:
               metaKey.compareTo(o.metaKey) ?: 0
    }

    def xml(builder) {
        builder.metadata() {
            builder.dateCreated(this.dateCreated)
            builder.lastUpdated(this.lastUpdated)
            builder.metaKey(this.metaKey)
            builder.metaKey { builder.mkp.yieldUnescaped("<![CDATA[${this.metaValue}]]>") }
            builder.parentRef(this.parentRef)
            builder.parentType(this.parentType)
            exportDomainsPlugins(builder)
        }
    }
}