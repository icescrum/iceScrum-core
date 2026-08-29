/*
 * Copyright (c) 2026 iceScrum community.
 *
 * This file is part of iceScrum.
 *
 * iceScrum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * Minimal replacement for the Grails 2 mail plugin (org.icescrum:mail fork),
 * which has no Grails 7 release. Exposes the same surface iceScrum uses:
 * sendMail { async / from / replyTo / envelopeFrom / to / cc / bcc / subject /
 * body(view:, plugin:, model:) }, plus mailExecutorService and
 * afterPropertiesSet() which NotificationEmailService calls to revive the
 * executor. Configuration is read from grails.mail.* at send time, so admin
 * config changes (BootStrapService) are picked up without restarting.
 */
package org.icescrum.core.services

import grails.gsp.PageRenderer
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.InitializingBean
import org.springframework.mail.javamail.JavaMailSenderImpl
import org.springframework.mail.javamail.MimeMessageHelper

import jakarta.mail.internet.MimeMessage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor

class MailService implements InitializingBean, DisposableBean {

    static transactional = false

    def grailsApplication
    PageRenderer groovyPageRenderer

    ExecutorService mailExecutorService

    @Override
    void afterPropertiesSet() {
        mailExecutorService = Executors.newFixedThreadPool(5)
    }

    @Override
    void destroy() {
        mailExecutorService?.shutdown()
    }

    def sendMail(Closure callable) {
        def message = new MailMessageSpec()
        callable.delegate = message
        callable.resolveStrategy = Closure.DELEGATE_FIRST
        callable.call()
        if (message.async) {
            // Warning: if async then errors cannot be caught by the caller (same as the old plugin)
            if (((ThreadPoolExecutor) mailExecutorService).isTerminated()) {
                afterPropertiesSet()
            }
            mailExecutorService.submit {
                try {
                    doSend(message)
                } catch (Exception e) {
                    log.error("Asynchronous mail sending failed: ${e.message}", e)
                }
            }
        } else {
            doSend(message)
        }
    }

    private void doSend(MailMessageSpec spec) {
        def mailConfig = grailsApplication.config.grails.mail
        JavaMailSenderImpl sender = new JavaMailSenderImpl()
        sender.host = mailConfig.host ?: 'localhost'
        if (mailConfig.port) {
            sender.port = mailConfig.port as int
        }
        if (mailConfig.username) {
            sender.username = mailConfig.username.toString()
        }
        if (mailConfig.password) {
            sender.password = mailConfig.password.toString()
        }
        Properties props = new Properties()
        if (mailConfig.props instanceof Map) {
            mailConfig.props.each { k, v -> props.put(k.toString(), v.toString()) }
        }
        if (spec.envelopeFrom) {
            props.put('mail.smtp.from', spec.envelopeFrom.toString()) // SMTP envelope sender (bounces)
        }
        sender.javaMailProperties = props

        String overrideAddress = mailConfig.overrideAddress ?: null
        MimeMessage mimeMessage = sender.createMimeMessage()
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, 'UTF-8')
        String from = spec.from ?: (mailConfig.'default'?.from ?: null)
        if (from) {
            helper.setFrom(from.toString())
        }
        if (spec.replyTo) {
            helper.setReplyTo(spec.replyTo.toString())
        }
        helper.setTo(addresses(spec.to, overrideAddress) ?: new String[0])
        String[] cc = addresses(spec.cc, overrideAddress)
        if (cc) {
            helper.setCc(cc)
        }
        String[] bcc = addresses(spec.bcc, overrideAddress)
        if (bcc) {
            helper.setBcc(bcc)
        }
        helper.setSubject(spec.subject.toString())
        String content
        if (spec.bodyView) {
            content = groovyPageRenderer.render(view: spec.bodyView, model: spec.bodyModel ?: [:])
        } else {
            content = spec.bodyText ?: ''
        }
        helper.setText(content, true)
        sender.send(mimeMessage)
    }

    private static String[] addresses(value, String overrideAddress) {
        if (value == null) {
            return null
        }
        def list = (value instanceof Collection || value?.class?.isArray()) ? value.toList() : [value]
        list = list.findAll { it }
        if (!list) {
            return null
        }
        if (overrideAddress) {
            list = list.collect { overrideAddress }.unique()
        }
        return list*.toString() as String[]
    }

    static class MailMessageSpec {
        boolean async = false
        def from
        def replyTo
        def envelopeFrom
        def to
        def cc
        def bcc
        def subject
        String bodyView
        Map bodyModel
        String bodyText

        void async(boolean value) { this.async = value }

        void from(value) { this.from = value }

        void replyTo(value) { this.replyTo = value }

        void envelopeFrom(value) { this.envelopeFrom = value }

        void to(value) { this.to = value }

        void cc(value) { this.cc = value }

        void bcc(value) { this.bcc = value }

        void subject(value) { this.subject = value }

        void body(Map args) {
            // 'plugin' is accepted for source compatibility; plugin views are
            // resolved globally by the PageRenderer in Grails 7
            this.bodyView = args.view
            this.bodyModel = (Map) args.model
        }

        void body(String text) {
            this.bodyText = text
        }
    }
}
