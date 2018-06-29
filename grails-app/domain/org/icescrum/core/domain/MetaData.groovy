package org.icescrum.core.domain

class MetaData implements Serializable, Comparable {

    String key
    String value

    Long parentRef
    String parentType

    Date dateCreated
    Date lastUpdated

    static constraints = {
        key blank: false, shared: 'keyMaxSize'
        value nullable: true
        parentType blank: false, shared: 'keyMaxSize'
    }

    static mapping = {
        cache true
        table 'is_metadata'
        parentRef index: 'metadata_parent_ref_index'
        parentType index: 'metadata_parent_type_index'
        key index: 'metadata_key_index'
    }

    @Override
    int compareTo(Object o) {
        return parentType.compareTo(o.parentType) ?:
               parentRef.compareTo(o.parentRef) ?:
               dateCreated.compareTo(o.dateCreated) ?:
               key.compareTo(o.code) ?:
               0
    }

    def xml(builder) {
        builder.metadata() {
            builder.dateCreated(this.dateCreated)
            builder.lastUpdated(this.lastUpdated)
            builder.key(this.key)
            builder.value { builder.mkp.yieldUnescaped("<![CDATA[${this.value}]]>") }
            builder.parentRef(this.parentRef)
            builder.parentType(this.parentType)
            exportDomainsPlugins(builder)
        }
    }
}