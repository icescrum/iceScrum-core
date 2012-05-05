package org.icescrum.core.event

import org.icescrum.core.domain.Product
import org.icescrum.core.domain.User
import org.icescrum.core.domain.Team
import org.apache.commons.io.FilenameUtils

/**
 * Created by IntelliJ IDEA.
 * User: vbarrier
 * Date: 21/02/11
 * Time: 01:03
 * To change this template use File | Settings | File Templates.
 */
class IceScrumProductEvent extends IceScrumEvent {
  def team = null
  def xml = null
  File importPath = null
  static final String EVENT_TEAM_ADDED = 'TeamAdded'
  static final String EVENT_TEAM_REMOVED = 'TeamRemoved'
  static final String EVENT_IMPORTED = 'productImported'

  IceScrumProductEvent(Product product, Class generatedBy, User doneBy, def type, boolean synchronous = false){
    super(product, generatedBy, doneBy, type, synchronous)
  }

  IceScrumProductEvent(Product product, File importPath, Class generatedBy, User doneBy, def type, boolean synchronous = false){
    super(product, generatedBy, doneBy, type, synchronous)
    this.importPath = importPath
    if (importPath.isDirectory()) {
        def xmlFile = importPath.listFiles().find { !it.isDirectory() && FilenameUtils.getExtension(it.name) == 'xml' }
        this.xml = new XmlSlurper().parse(xmlFile)
    } else {
        this.xml = new XmlSlurper().parse(importPath)
    }
    //be compatible with xml without export tag
    if (this.xml.find{it.name == 'export'}){
        this.xml = this.xml.product
    }
  }

  IceScrumProductEvent(Product product, Team team, Class generatedBy, User doneBy, def type, boolean synchronous = false){
    super(product, generatedBy, doneBy, type, synchronous)
    this.team = team
  }
}
