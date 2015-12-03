/*
 * Copyright (c) 2014 Kagilum SAS.
 *
 * This file is part of iceScrum.
 *
 * iceScrum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * iceScrum is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with iceScrum.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Authors:
 *
 * Vincent Barrier (vbarrier@kagilum.com)
 * Nicolas Noullet (nnoullet@kagilum.com)
 */

package org.icescrum.core.services

import grails.plugin.cache.Cacheable
import grails.plugin.springsecurity.SpringSecurityUtils
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.apache.commons.io.filefilter.WildcardFileFilter
import org.icescrum.core.domain.*
import org.icescrum.core.domain.Invitation.InvitationType
import org.icescrum.core.domain.preferences.UserPreferences
import org.icescrum.core.domain.security.Authority
import org.icescrum.core.domain.security.UserAuthority
import org.icescrum.core.event.IceScrumEventPublisher
import org.icescrum.core.event.IceScrumEventType
import org.icescrum.core.support.ApplicationSupport
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Transactional

@Transactional
class UserService extends IceScrumEventPublisher {

    def grailsApplication
    def productService
    def springSecurityService
    def hdImageService
    def notificationEmailService

    void save(User user, String token = null) {
        if (!user.validate()) {
            throw new RuntimeException()
        }
        user.password = springSecurityService.encodePassword(user.password)
        if (!user.save()) {
            throw new RuntimeException()
        }
        publishSynchronousEvent(IceScrumEventType.CREATE, user)
        if (token && grailsApplication.config.icescrum.invitation.enable) {
            def invitations = Invitation.findAllByToken(token)
            invitations.each { invitation ->
                def userAdmin = UserAuthority.findByAuthority(Authority.findByAuthority(Authority.ROLE_ADMIN)).user
                SpringSecurityUtils.doWithAuth(userAdmin ? userAdmin.username : 'admin') {
                    if (invitation.type == InvitationType.PRODUCT) {
                        Product product = invitation.product
                        def oldMembers = productService.getAllMembersProductByRole(product)
                        productService.addRole(product, user, invitation.futureRole)
                        productService.manageProductEvents(product, oldMembers)
                    } else {
                        Team team = invitation.team
                        def oldMembersByProduct = [:]
                        team.products.each { Product product ->
                            oldMembersByProduct[product.id] = productService.getAllMembersProductByRole(product)
                        }
                        productService.addRole(team, user, invitation.futureRole)
                        oldMembersByProduct.each { Long productId, Map oldMembers ->
                            productService.manageProductEvents(Product.get(productId), oldMembers)
                        }
                    }
                }
            }
        }
    }

    void update(User user, Map props) {

        if (props.pwd) {
            user.password = springSecurityService.encodePassword(props.pwd)
        }
        if (props.emailsSettings) {
            user.preferences.emailsSettings = props.emailsSettings
        }
        try {
            def ext = FilenameUtils.getExtension(props.avatar)
            def path = "${grailsApplication.config.icescrum.images.users.dir}${user.id}.${ext}"
            if (props.avatar) {
                def source = new File((String) props.avatar)
                def dest = new File(path)
                FileUtils.copyFile(source, dest)
                if (props.scale) {
                    def avatar = new File(path)
                    avatar.setBytes(hdImageService.scale(new FileInputStream(dest), 120, 120))
                }
                def files = new File(grailsApplication.config.icescrum.images.users.dir.toString()).listFiles((FilenameFilter) new WildcardFileFilter("${user.id}.*"))
                files.each {
                    if (FilenameUtils.getExtension(it.path) != ext) {
                        it.delete()
                    }
                }
            } else if (props.containsKey('avatar') && props.avatar == null) {
                File[] oldAvatars = new File(grailsApplication.config.icescrum.images.users.dir.toString()).listFiles((FilenameFilter) new WildcardFileFilter("${user.id}.*"))
                oldAvatars.each {
                    it.delete()
                }
            }
        }
        catch (RuntimeException e) {
            if (log.debugEnabled) e.printStackTrace()
            throw new RuntimeException('is.convert.image.error')
        }
        user.lastUpdated = new Date()
        def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_UPDATE, user)
        if (!user.save()) {
            throw new RuntimeException(user.errors?.toString())
        }
        publishSynchronousEvent(IceScrumEventType.UPDATE, user, dirtyProperties)
    }

    @PreAuthorize("ROLE_ADMIN")
    boolean delete(User user) {
        try {
            def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_DELETE, user)
            user.delete()
            publishSynchronousEvent(IceScrumEventType.DELETE, user, dirtyProperties)
            return true
        } catch (Exception e) {
            return false
        }
    }

    def resetPassword(User user) {
        def pool = ['a'..'z', 'A'..'Z', 0..9, '_'].flatten()
        Random rand = new Random(System.currentTimeMillis())
        def passChars = (0..10).collect { pool[rand.nextInt(pool.size())] }
        def password = passChars.join('')
        update(user, [pwd: password])
        notificationEmailService.sendNewPassword(user, password)
    }


    void menu(User user, String id, String position, boolean hidden) {
        def currentMenu
        if (hidden) {
            currentMenu = user.preferences.menuHidden
            if (!currentMenu.containsKey(id)) {
                currentMenu.put(id, (currentMenu.size() + 1).toString())
                if (user.preferences.menu.containsKey(id)) {
                    this.menu(user, id, user.preferences.menu.size().toString(), false)
                    user.preferences.menu.remove(id)
                }
            }
        } else {
            currentMenu = user.preferences.menu
            if (!currentMenu.containsKey(id)) {
                currentMenu.put(id, (currentMenu.size() + 1).toString())
                if (user.preferences.menuHidden.containsKey(id)) {
                    this.menu(user, id, user.preferences.menuHidden.size().toString(), true)
                    user.preferences.menuHidden.remove(id)
                }
            }
        }
        def from = currentMenu.get(id)?.toInteger()
        from = from ?: 1
        def to = position.toInteger()
        if (from != to) {
            if (from > to) {
                currentMenu.entrySet().each { it ->
                    if (it.value.toInteger() >= to && it.value.toInteger() <= from && it.key != id) {
                        it.value = (it.value.toInteger() + 1).toString()
                    } else if (it.key == id) {
                        it.value = position
                    }
                }
            } else {
                currentMenu.entrySet().each { it ->
                    if (it.value.toInteger() <= to && it.value.toInteger() >= from && it.key != id) {
                        it.value = (it.value.toInteger() - 1).toString()
                    } else if (it.key == id) {
                        it.value = position
                    }
                }
            }
        }
        user.lastUpdated = new Date()
        if (!user.save()) {
            throw new RuntimeException()
        }
    }

    @Transactional(readOnly = true)
    def unMarshall(def user) {
        try {
            def u
            if (user.@uid.text()) {
                u = User.findByUid(user.@uid.text())
            } else {
                u = ApplicationSupport.findUserUIDOldXMl(user, null, null)
            }
            if (!u) {
                u = new User(
                        lastName: user.lastName.text(),
                        firstName: user.firstName.text(),
                        username: user.username.text(),
                        email: user.email.text(),
                        password: user.password.text(),
                        enabled: user.enabled.text().toBoolean() ?: true,
                        accountExpired: user.accountExpired.text().toBoolean() ?: false,
                        accountLocked: user.accountLocked.text().toBoolean() ?: false,
                        passwordExpired: user.passwordExpired.text().toBoolean() ?: false,
                        accountExternal: user.accountExternal?.text()?.toBoolean() ?: false,
                        uid: user.@uid.text() ?: (user.username.text() + user.email.text()).encodeAsMD5()
                )

                def language = user.preferences.language.text()
                if (language == "en") {
                    def version = ApplicationSupport.findIceScrumVersionFromXml(user)
                    if (version == null || version < "R6#2") {
                        language = "en_US"
                    }
                }
                u.preferences = new UserPreferences(
                        language: language,
                        activity: user.preferences.activity.text(),
                        filterTask: user.preferences.filterTask.text(),
                        menu: user.preferences.menu.text(),
                        menuHidden: user.preferences.menuHidden.text(),
                        hideDoneState: user.preferences.hideDoneState.text()?.toBoolean() ?: false
                )
            }
            return u
        } catch (Exception e) {
            if (log.debugEnabled) e.printStackTrace()
            throw new RuntimeException(e)
        }
    }

    void updatePanelPosition(User user, String id, String position, boolean right) {
        def currentPanels
        if (right) {
            currentPanels = user.preferences.panelsRight
            if (!currentPanels.containsKey(id)) {
                currentPanels.put(id, (currentPanels.size()).toString())
                if (user.preferences.panelsLeft.containsKey(id)) {
                    updatePanelPosition(user, id, (user.preferences.panelsLeft.size() - 1).toString(), false)
                    user.preferences.panelsLeft.remove(id)
                }
            }
        } else {
            currentPanels = user.preferences.panelsLeft
            if (!currentPanels.containsKey(id)) {
                currentPanels.put(id, (currentPanels.size()).toString())
                if (user.preferences.panelsRight.containsKey(id)) {
                    updatePanelPosition(user, id, (user.preferences.panelsRight.size() - 1).toString(), true)
                    user.preferences.panelsRight.remove(id)
                }
            }
        }
        def from = currentPanels.get(id)?.toInteger()
        from = from ?: 0
        def to = position.toInteger()
        if (from != to) {
            if (from > to) {
                currentPanels.entrySet().each { it ->
                    if (it.value.toInteger() >= to && it.value.toInteger() <= from && it.key != id) {
                        it.value = (it.value.toInteger() + 1).toString()
                    } else if (it.key == id) {
                        it.value = position
                    }
                }
            } else {
                currentPanels.entrySet().each { it ->
                    if (it.value.toInteger() <= to && it.value.toInteger() >= from && it.key != id) {
                        it.value = (it.value.toInteger() - 1).toString()
                    } else if (it.key == id) {
                        it.value = position
                    }
                }
            }
        }
        user.lastUpdated = new Date()
        if (!user.save()) {
            throw new RuntimeException()
        }
    }

    void saveFeed(User user, Feed feed) {
        user.preferences.feed = feed
        user.lastUpdated = new Date()
        if (!user.save()) {
            throw new RuntimeException()
        }
    }

    @Cacheable("feed")
    def getFeedContent(def url){
        def channel = new XmlSlurper().parse(url).channel
        def contentFeed = [title: channel.title.text(), description: channel.description.text()]
        contentFeed.items = channel.item.collect { xmlItem ->
            return [feedTitle: channel.title.text(),
                    link: xmlItem.link.text(),
                    title: xmlItem.title.text(),
                    description: xmlItem.description.text(),
                    pubDate: Date.parse("EEE', 'dd' 'MMM' 'yyyy' 'HH:mm:ss' 'Z", xmlItem.pubDate.text()).time]
        }
        return contentFeed
    }
}