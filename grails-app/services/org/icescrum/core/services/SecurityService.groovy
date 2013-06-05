/*
 * Copyright (c) 2010 iceScrum Technologies.
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
 * Vincent Barrier (vbarrier@kagilum.com)
 * Stéphane Maldini (stephane.maldini@icescrum.com)
 */

package org.icescrum.core.services

import org.springframework.security.core.context.SecurityContextHolder as SCH
import org.springframework.web.context.request.RequestContextHolder as RCH

import grails.plugin.springcache.key.CacheKeyBuilder
import org.codehaus.groovy.grails.orm.hibernate.cfg.GrailsHibernateUtil
import org.codehaus.groovy.grails.plugins.springsecurity.SpringSecurityUtils
import org.codehaus.groovy.grails.plugins.springsecurity.acl.AclClass
import org.codehaus.groovy.grails.plugins.springsecurity.acl.AclObjectIdentity
import org.codehaus.groovy.grails.plugins.springsecurity.acl.AclSid
import org.icescrum.core.domain.Product
import org.icescrum.core.domain.Team
import org.icescrum.core.domain.User
import org.icescrum.core.domain.security.Authority
import org.icescrum.core.domain.security.UserAuthority
import org.springframework.security.acls.domain.BasePermission
import org.springframework.security.acls.domain.PrincipalSid
import org.springframework.security.core.Authentication
import org.springframework.util.Assert
import static org.springframework.security.acls.domain.BasePermission.*
import org.springframework.security.acls.model.*

class SecurityService {

    static transactional = true

    def aclUtilService
    def objectIdentityRetrievalStrategy
    def springSecurityService
    def grailsUrlMappingsHolder
    def springcacheService
    def grailsApplication
    def aclService


    static final String TEAM_ATTR = 'team_id'
    static final String TEAM_URL_ATTR = 'team'
    static final String PRODUCT_ATTR = 'product_id'
    static final String PRODUCT_URL_ATTR = 'product'



    static final CACHE_TEAMMEMBER = 'teamMemberCache'
    static final CACHE_PRODUCTOWNER = 'productOwnerCache'
    static final CACHE_SCRUMMASTER = 'scrumMasterCache'
    static final CACHE_STAKEHOLDER = 'stakeHolderCache'
    static final CACHE_PRODUCTTEAM = 'productTeamCache'
    static final CACHE_OPENPRODUCTTEAM = 'teamProductCache'
    static final CACHE_OWNER = 'ownerCache'
    static final CACHE_ARCHIVEDPRODUCT = 'archivedProductCache'

    static final productOwnerPermissions = [BasePermission.WRITE]
    static final stakeHolderPermissions = [BasePermission.READ]
    static final teamMemberPermissions = [BasePermission.READ]
    static final scrumMasterPermissions = [BasePermission.WRITE]

    Acl secureDomain(o) {
        createAcl objectIdentityRetrievalStrategy.getObjectIdentity(o)
    }


    Acl secureDomain(o, parent) {
        createAcl objectIdentityRetrievalStrategy.getObjectIdentity(o), aclService.retrieveObjectIdentity(objectIdentityRetrievalStrategy.getObjectIdentity(parent))
    }

    Acl secureDomainByProduct(o, Product product) {
        createAcl objectIdentityRetrievalStrategy.getObjectIdentity(o), aclService.retrieveObjectIdentity(objectIdentityRetrievalStrategy.getObjectIdentity(product))
    }

    void unsecureDomain(o) {
        aclUtilService.deleteAcl GrailsHibernateUtil.unwrapIfProxy(o)
    }

    void changeOwner(User u, o) {
        aclUtilService.changeOwner GrailsHibernateUtil.unwrapIfProxy(o), u.username
        u.lastUpdated = new Date()
        u.save()
    }

    void createProductOwnerPermissions(User u, Product p) {
        aclUtilService.addPermission GrailsHibernateUtil.unwrapIfProxy(p), u.username, WRITE
        u.lastUpdated = new Date()
        u.save()
    }

    void deleteProductOwnerPermissions(User u, Product p) {
        aclUtilService.deletePermission GrailsHibernateUtil.unwrapIfProxy(p), u.username, WRITE
        u.lastUpdated = new Date()
        u.save()
    }

    void createTeamMemberPermissions(User u, Team t) {
        aclUtilService.addPermission GrailsHibernateUtil.unwrapIfProxy(t), u.username, READ
        u.lastUpdated = new Date()
        u.save()
    }

    void deleteTeamMemberPermissions(User u, Team t) {
        aclUtilService.deletePermission GrailsHibernateUtil.unwrapIfProxy(t), u.username, READ
        u.lastUpdated = new Date()
        u.save()
    }

    void createAdministrationPermissionsForProduct(User u, Product p){
        aclUtilService.addPermission GrailsHibernateUtil.unwrapIfProxy(p), u.username, ADMINISTRATION
        u.lastUpdated = new Date()
        u.save()
    }

    void createScrumMasterPermissions(User u, Team t) {
        aclUtilService.addPermission GrailsHibernateUtil.unwrapIfProxy(t), u.username, WRITE
        aclUtilService.addPermission GrailsHibernateUtil.unwrapIfProxy(t), u.username, ADMINISTRATION
        u.lastUpdated = new Date()
        u.save()
    }

    void deleteScrumMasterPermissions(User u, Team t) {
        aclUtilService.deletePermission GrailsHibernateUtil.unwrapIfProxy(t), u.username, WRITE
        aclUtilService.deletePermission GrailsHibernateUtil.unwrapIfProxy(t), u.username, ADMINISTRATION
        u.lastUpdated = new Date()
        u.save()
    }

    void createStakeHolderPermissions(User u, Product p) {
        aclUtilService.addPermission GrailsHibernateUtil.unwrapIfProxy(p), u.username, READ
        u.lastUpdated = new Date()
        u.save()
    }

    void deleteStakeHolderPermissions(User u, Product p) {
        aclUtilService.deletePermission GrailsHibernateUtil.unwrapIfProxy(p), u.username, READ
        u.lastUpdated = new Date()
        u.save()
    }

    @SuppressWarnings("GroovyMissingReturnStatement")
    boolean inProduct(product, auth) {
        if (!springSecurityService.isLoggedIn())
            return false

        boolean authorized = productOwner(product, auth)
        if (!authorized) {
            def p
            if (!product){
                def request = RCH.requestAttributes.currentRequest
                if (request.filtered)
                    return request.inProduct
                else
                    product = parseCurrentRequestProduct(request)
            }
            else if (product in Product) {
                p = product
                product = product.id
            }
            if (product) {
                authorized = springcacheService.doWithCache(CACHE_PRODUCTTEAM, new CacheKeyBuilder().append(product).append(auth.principal.id).append(getUserLastUpdated(auth.principal.id)).toCacheKey()) {
                    if (!p) p = Product.get(product)
                    if (!p || !auth) return false
                    //Check if he is ScrumMaster or Member
                    for (team in p.teams) {
                        if (inTeam(team, auth)) {
                            return true
                        }
                    }
                }
            }
        }

        return authorized
    }

    boolean archivedProduct(product){
        def p
        if (SpringSecurityUtils.ifAnyGranted(Authority.ROLE_ADMIN))
            return false

        if (!product){
                def request = RCH.requestAttributes.currentRequest
                if (request.filtered)
                    return request.archivedProduct ?: false
                else
                    product = parseCurrentRequestProduct(request)
            }else if (product in Product) {
                p = product
                product = product.id
            }
            if (product) {
                return springcacheService.doWithCache(CACHE_ARCHIVEDPRODUCT, new CacheKeyBuilder().append(product).append(getProductLastUpdated(product)).toCacheKey()) {
                    if (!p) p = Product.get(product)
                    if (!p) return false
                    return p.preferences.archived
                }
            }else{
                return null
            }
    }

    boolean inTeam(team, auth) {
        if (!springSecurityService.isLoggedIn())
            return false
        teamMember(team, auth) || scrumMaster(team, auth)
    }

    Team openProductTeam(Long productId, Long principalId) {
        springcacheService.doWithCache(CACHE_OPENPRODUCTTEAM, new CacheKeyBuilder().append(productId).append(principalId).append(getUserLastUpdated(principalId)).toCacheKey()) {
            def team = Team.productTeam(productId, principalId).list(max: 1)
            if (team)
                team[0]
            else
                null
        }

    }


    boolean scrumMaster(team, auth) {
        if (!springSecurityService.isLoggedIn())
            return false

        def t = null

        if (!team) {
            def request = RCH.requestAttributes.currentRequest
            if (request.filtered)
                return request.scrumMaster
            else {
                def parsedProduct = parseCurrentRequestProduct(request)
                if (parsedProduct) {
                    if (SpringSecurityUtils.ifAnyGranted(Authority.ROLE_ADMIN))
                        return true
                    t = openProductTeam(parsedProduct, springSecurityService.principal.id)
                    team = t?.id
                }
            }
        }
        else if (team in Team) {
            t = GrailsHibernateUtil.unwrapIfProxy(team)
            team = t.id
        }

        isScrumMaster(team, auth, t)
    }

    boolean isScrumMaster(team, auth, t = null) {
        if (team) {

            if (SpringSecurityUtils.ifAnyGranted(Authority.ROLE_ADMIN))
                return true

            def res = springcacheService.doWithCache(CACHE_SCRUMMASTER, new CacheKeyBuilder().append(team).append(auth.principal.id).append(getUserLastUpdated(auth.principal.id)).toCacheKey()) {
                if (!t) t = Team.get(team)
                if (!t || !auth) return false
                return aclUtilService.hasPermission(auth, GrailsHibernateUtil.unwrapIfProxy(t), SecurityService.scrumMasterPermissions)
            }
            return res
        }
        else
            return false
    }


    boolean stakeHolder(product, auth, onlyPrivate, controllerName = null) {

        if (!springSecurityService.isLoggedIn() && onlyPrivate)
            return false

        def p = null

        if (!product){
            def request = RCH.requestAttributes.currentRequest
            if (request.filtered  && !controllerName)
                return request.stakeHolder
            else
                product = parseCurrentRequestProduct(request)
        } else if (product in Product) {
            p = GrailsHibernateUtil.unwrapIfProxy(product)
            product = product.id
        }

        if (product) {

            if (!p) p = Product.get(product)
            if (!p || !auth) return false

            if (SpringSecurityUtils.ifAnyGranted(Authority.ROLE_ADMIN))
                return true

            def authkey = SpringSecurityUtils.ifAnyGranted(Authority.ROLE_VISITOR) ? auth.principal : auth.principal.id + getUserLastUpdated(auth.principal.id).toString() + controllerName ?: ''
            return springcacheService.doWithCache(CACHE_STAKEHOLDER, new CacheKeyBuilder().append(onlyPrivate).append(product).append(p.lastUpdated).append(authkey).toCacheKey()) {
                //Owner always has an access to product... (even if not in team or PO)
                if (springSecurityService.isLoggedIn()){
                    if (p.owner?.id == auth.principal.id) return true
                }
                def access = p.preferences.hidden ? aclUtilService.hasPermission(auth, GrailsHibernateUtil.unwrapIfProxy(p), SecurityService.stakeHolderPermissions) : !onlyPrivate
                if (access && controllerName){
                    return !(controllerName in p.preferences.stakeHolderRestrictedViews?.split(','))
                }else{
                    return access
                }
            }

        }
        else
            return false
    }

    boolean productOwner(product, auth) {
        if (!springSecurityService.isLoggedIn())
            return false

        def p = null

        if (!product){
            def request = RCH.requestAttributes.currentRequest
            if (request.filtered)
                return request.productOwner
            else
                product = parseCurrentRequestProduct(request)
        }else if (product in Product) {
            p = GrailsHibernateUtil.unwrapIfProxy(product)
            product = product.id
        }

        isProductOwner(product, auth, p)
    }

    boolean admin(auth) {
        if (!springSecurityService.isLoggedIn())
            return false

        return SpringSecurityUtils.ifAnyGranted(Authority.ROLE_ADMIN)
    }

    boolean isProductOwner(product, auth, p = null) {
        if (product) {

            if (SpringSecurityUtils.ifAnyGranted(Authority.ROLE_ADMIN))
                return true

            return springcacheService.doWithCache(CACHE_PRODUCTOWNER, new CacheKeyBuilder().append(product).append(auth.principal.id).append(getUserLastUpdated(auth.principal.id)).toCacheKey()) {
                if (!p) p = Product.get(product)
                if (!p || !auth) return false
                return aclUtilService.hasPermission(auth, GrailsHibernateUtil.unwrapIfProxy(p), SecurityService.productOwnerPermissions)
            }
        }
        else
            return false
    }

    boolean teamMember(team, auth) {
        if (!springSecurityService.isLoggedIn())
            return false

        def t

        if (!team) {
            def request = RCH.requestAttributes.currentRequest
            if (request.filtered)
                return request.inTeam
            else{
                def parsedProduct = parseCurrentRequestProduct(request)
                if (parsedProduct) {
                    if (SpringSecurityUtils.ifAnyGranted(Authority.ROLE_ADMIN))
                        return true
                    t = openProductTeam(parsedProduct, springSecurityService.principal.id)
                    team = t?.id
                }
            }
        }
        else if (team in Team) {
            t = GrailsHibernateUtil.unwrapIfProxy(team)
            team = team.id
        }

        if (team) {

            if (SpringSecurityUtils.ifAnyGranted(Authority.ROLE_ADMIN))
                return true

            return springcacheService.doWithCache(CACHE_TEAMMEMBER, new CacheKeyBuilder().append(team).append(auth.principal.id).append(getUserLastUpdated(auth.principal.id)).toCacheKey()) {
                if (!t) t = Team.get(team)
                if (!t || !auth) return false
                return aclUtilService.hasPermission(auth, GrailsHibernateUtil.unwrapIfProxy(t), SecurityService.teamMemberPermissions)
            }
        }
        else
            return false
    }

    boolean hasRoleAdmin(User user) {
        UserAuthority.countByAuthorityAndUser(Authority.findByAuthority(Authority.ROLE_ADMIN, [cache: true]), user, [cache: true])
    }

    Long parseCurrentRequestProduct(request) {
        def res = request[PRODUCT_ATTR]
        if (!res) {
            def param = request.getParameter(PRODUCT_URL_ATTR)
            if (!param) {
                def mappingInfo = grailsUrlMappingsHolder.match(request.forwardURI.replaceFirst(request.contextPath, ''))
                res = mappingInfo?.parameters?.getAt(PRODUCT_URL_ATTR)?.decodeProductKey()?.toLong()
            } else {
                res = param?.decodeProductKey()?.toLong()
            }
            request[PRODUCT_ATTR] = res
        }

        res
    }

    MutableAcl createAcl(ObjectIdentity objectIdentity, parent = null) throws AlreadyExistsException {
        Assert.notNull objectIdentity, 'Object Identity required'

        // Check this object identity hasn't already been persisted
        if (aclService.retrieveObjectIdentity(objectIdentity)) {
            throw new AlreadyExistsException("Object identity '$objectIdentity' already exists")
        }

        // Need to retrieve the current principal, in order to know who "owns" this ACL (can be changed later on)
        PrincipalSid sid = new PrincipalSid(SCH.context.authentication)

        // Create the acl_object_identity row
        createObjectIdentity objectIdentity, sid, parent

        return aclService.readAclById(objectIdentity)
    }

    protected void createObjectIdentity(ObjectIdentity object, Sid owner, parent = null) {
        AclSid ownerSid = aclService.createOrRetrieveSid(owner, true)
        AclClass aclClass = aclService.createOrRetrieveClass(object.type, true)
        aclService.save new AclObjectIdentity(
                aclClass: aclClass,
                objectId: object.identifier,
                owner: ownerSid,
                parent: parent,
                entriesInheriting: true)
    }

    public boolean owner(domain, Authentication auth) {
        if (!springSecurityService.isLoggedIn())
            return false

        if (SpringSecurityUtils.ifAnyGranted(Authority.ROLE_ADMIN))
            return true

        def d = null
        def parsedDomain
        def domainClass

        if (!domain) {
            def request = RCH.requestAttributes.currentRequest
            if (request.filtered)
                return request.owner
            else{
                parsedDomain = parseCurrentRequestProduct(request)
                domain = parsedDomain
                domainClass = grailsApplication.getDomainClass(Product.class.name).newInstance()
            }
        } else {
            d = GrailsHibernateUtil.unwrapIfProxy(domain)
            domainClass = d
            if (!d) return false
            domain = d.id
        }
        isOwner(domain, auth, domainClass, d)
    }

    boolean isOwner(domain, auth, domainClass, d = null) {
        if (domain && domainClass) {
            return springcacheService.doWithCache(CACHE_OWNER, new CacheKeyBuilder().append(domain).append(domainClass.class.name).append(auth.principal.id).append(getUserLastUpdated(auth.principal.id)).toCacheKey()) {
                if (!d) d = domainClass.get(domain)

                if (!d || !auth) return false

                def acl = aclService.readAclById(objectIdentityRetrievalStrategy.getObjectIdentity(d))
                return acl.owner == new PrincipalSid((Authentication) auth)
            }
        }
        else
            return false
    }

    def filterRequest(){
        def request = RCH.requestAttributes.currentRequest

        if (!request || (request && request.filtered))
            return

        request.authenticated =  springSecurityService.isLoggedIn()
        request.scrumMaster   = request.scrumMaster ?: scrumMaster(null,springSecurityService.authentication)
        request.productOwner  = request.productOwner ?: productOwner(null,springSecurityService.authentication)
        request.teamMember    = request.teamMember ?: teamMember(null,springSecurityService.authentication)
        request.stakeHolder   = request.stakeHolder ?: stakeHolder(null,springSecurityService.authentication,false)
        request.owner         = request.owner ?: owner(null,springSecurityService.authentication)
        request.inProduct     = request.inProduct ?: request.scrumMaster ?: request.productOwner ?: request.teamMember ?: false
        request.inTeam        = request.inTeam ?: request.scrumMaster ?: request.teamMember ?: false
        request.admin         = request.admin ?: admin(springSecurityService.authentication) ?: false

        if (request.owner && !request.inProduct && !request.admin){
            request.stakeholder = true
        }

        if ((request.inProduct || request.stakeHolder) && archivedProduct(null)){
            request.scrumMaster     = false
            request.productOwner    = false
            request.teamMember      = false
            request.inTeam          = false
            request.inProduct       = false
            request.owner           = false
            request.archivedProduct = true
        }

        request.filtered = request.filtered ?: true
    }

    def getUserLastUpdated(id){
        User.createCriteria().get {
          eq 'id', id
            projections {
               property 'lastUpdated'
            }
          cache true
        }
    }

    def getProductLastUpdated(id){
        Product.createCriteria().get {
          eq 'id', id
            projections {
               property 'lastUpdated'
            }
          cache true
        }
    }

}
