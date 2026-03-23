/*
 * Copyright (c) 2002-2014, Mairie de Paris
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice
 *     and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright notice
 *     and the following disclaimer in the documentation and/or other materials
 *     provided with the distribution.
 *
 *  3. Neither the name of 'Mairie de Paris' nor 'Lutece' nor the names of its
 *     contributors may be used to endorse or promote products derived from
 *     this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 * License 1.0
 */
package fr.paris.lutece.plugins.seo.modules.wiki.service;

import fr.paris.lutece.plugins.seo.business.FriendlyUrl;
import fr.paris.lutece.plugins.seo.service.FriendlyUrlUtils;
import fr.paris.lutece.plugins.seo.service.SEODataKeys;
import fr.paris.lutece.plugins.seo.service.generator.FriendlyUrlGenerator;
import fr.paris.lutece.plugins.seo.service.generator.GeneratorOptions;
import fr.paris.lutece.plugins.seo.service.sitemap.SitemapUtils;
import fr.paris.lutece.plugins.wiki.business.item.AbstractWikiItem;
import fr.paris.lutece.plugins.wiki.business.item.WikiItemHome;
import fr.paris.lutece.plugins.wiki.business.item.WikiItemType;
import fr.paris.lutece.plugins.wiki.business.revision.Revision;
import fr.paris.lutece.plugins.wiki.business.revision.RevisionHome;
import fr.paris.lutece.portal.service.datastore.DatastoreService;
import fr.paris.lutece.portal.service.util.AppLogService;
import fr.paris.lutece.portal.service.util.AppPropertiesService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

import java.text.MessageFormat;
import java.util.List;


/**
 * Wiki Friendly URL Generator for Lutece 8 wiki hierarchy (Space / Book / Page)
 */
@ApplicationScoped
@Named
public class WikiFriendlyUrlGenerator implements FriendlyUrlGenerator
{
    private static final String GENERATOR_NAME = "Wiki Friendly URL Generator";
    private static final String SLASH = "/";
    private static final String PATH_WIKI = "wiki/";

    // Technical URL templates with raw ampersands (Freemarker auto-escaping handles XML encoding)
    private static final String TECHNICAL_URL_PAGE = "/jsp/site/Portal.jsp?page=wiki&view=viewPage&book={0}&wiki_page={1}";
    private static final String TECHNICAL_URL_BOOK = "/jsp/site/Portal.jsp?page=wiki&view=viewBook&book={0}";
    private static final String TECHNICAL_URL_SPACE = "/jsp/site/Portal.jsp?page=wiki&view=viewSpace&space={0}";
    private static final String TECHNICAL_URL_HOME = "/jsp/site/Portal.jsp?page=wiki";
    private static final String FRIENDLY_URL_HOME = "/wiki/home";

    private static final String PROPERTY_PAGE_NAME_BASED_URL_ACTIVATE = "seo-wiki.pageNameBasedExplicitUrl.activate";
    private static final String PROPERTY_PAGE_NAME_BASED_URL_TEMPLATE = "seo-wiki.pageNameBasedExplicitUrl.template";
    private static final String PROPERTY_PAGE_TITLE_BASED_URLS_ACTIVATE = "seo-wiki.pageTitleBasedExplicitUrls.activate";
    private static final String PROPERTY_PAGE_TITLE_BASED_URLS_TEMPLATE = "seo-wiki.pageTitleBasedExplicitUrls.template";

    private static final String DEFAULT_CHANGE_FREQ = SitemapUtils.CHANGE_FREQ_VALUES[3];
    private static final String DEFAULT_PRIORITY = SitemapUtils.PRIORITY_VALUES[3];
    private boolean _bCanonical;
    private boolean _bSitemap;
    private String _strChangeFreq;
    private String _strPriority;

    /**
     * {@inheritDoc }
     */
    @Override
    public String generate( List<FriendlyUrl> list, GeneratorOptions options )
    {
        init( );

        // Generate URL for wiki home page (listWiki view)
        FriendlyUrl homeUrl = createFriendlyUrl( FRIENDLY_URL_HOME, TECHNICAL_URL_HOME, null );
        list.add( homeUrl );

        // Generate URLs for wiki pages (inside books)
        List<AbstractWikiItem> listPages = WikiItemHome.getWikiItemsByType( WikiItemType.PAGE );
        for ( AbstractWikiItem page : listPages )
        {
            generatePageUrls( page, list, options );
        }

        // Generate URLs for books
        List<AbstractWikiItem> listBooks = WikiItemHome.getWikiItemsByType( WikiItemType.BOOK );
        for ( AbstractWikiItem book : listBooks )
        {
            generateBookUrl( book, list, options );
        }

        // Generate URLs for spaces
        List<AbstractWikiItem> listSpaces = WikiItemHome.getWikiItemsByType( WikiItemType.SPACE );
        for ( AbstractWikiItem space : listSpaces )
        {
            generateSpaceUrl( space, list, options );
        }

        return "";
    }

    /**
     * Generate friendly URLs for a wiki page
     */
    private void generatePageUrls( AbstractWikiItem page, List<FriendlyUrl> list, GeneratorOptions options )
    {
        Revision currentRevision = RevisionHome.getCurrentRevision( page.getId( ) );

        if ( currentRevision == null )
        {
            AppLogService.error( "SEO Wiki indexer : No revision was found for wiki item #{}. Data may be corrupted.", page.getId( ) );
            return;
        }

        // Find the parent book by traversing up the hierarchy
        String strBookCode = findParentBookCode( page );
        if ( strBookCode == null )
        {
            AppLogService.error( "SEO Wiki indexer : No parent book found for wiki page #{} (code={}). Skipping.", page.getId( ), page.getCode( ) );
            return;
        }

        String strTechnicalUrl = MessageFormat.format( TECHNICAL_URL_PAGE, strBookCode, page.getCode( ) );

        Boolean generatePageNameBasedExplicitURL = "true".equals( AppPropertiesService.getProperty( PROPERTY_PAGE_NAME_BASED_URL_ACTIVATE ) );
        Boolean generatePageTitleBasedExplicitURLs = "true".equals( AppPropertiesService.getProperty( PROPERTY_PAGE_TITLE_BASED_URLS_ACTIVATE ) );

        if ( generatePageNameBasedExplicitURL )
        {
            String template = AppPropertiesService.getProperty( PROPERTY_PAGE_NAME_BASED_URL_TEMPLATE );
            String wikiPath = ( options.isAddPath( ) ? PATH_WIKI : "" );
            String pageName = FriendlyUrlUtils.convertToFriendlyUrl( page.getCode( ) );
            String strPath = SLASH + MessageFormat.format( template, wikiPath, pageName, "", "" );

            FriendlyUrl url = createFriendlyUrl( strPath, strTechnicalUrl, currentRevision );
            list.add( url );
        }

        if ( generatePageTitleBasedExplicitURLs )
        {
            String template = AppPropertiesService.getProperty( PROPERTY_PAGE_TITLE_BASED_URLS_TEMPLATE );
            String revisionTitle = currentRevision.getTitle( );
            String pageTitle = FriendlyUrlUtils.convertToFriendlyUrl(
                ( revisionTitle != null && !revisionTitle.isEmpty( ) ) ? revisionTitle : page.getCode( ) );
            String wikiPath = ( options.isAddPath( ) ? PATH_WIKI : "" );
            String pageName = FriendlyUrlUtils.convertToFriendlyUrl( page.getCode( ) );
            String strPath = SLASH + MessageFormat.format( template, wikiPath, pageName, pageTitle, "" );

            FriendlyUrl url = createFriendlyUrl( strPath, strTechnicalUrl, currentRevision );
            list.add( url );
        }
    }

    /**
     * Generate friendly URL for a wiki book
     */
    private void generateBookUrl( AbstractWikiItem book, List<FriendlyUrl> list, GeneratorOptions options )
    {
        Revision currentRevision = RevisionHome.getCurrentRevision( book.getId( ) );

        if ( currentRevision == null )
        {
            return;
        }

        String strTechnicalUrl = MessageFormat.format( TECHNICAL_URL_BOOK, book.getCode( ) );
        String wikiPath = ( options.isAddPath( ) ? PATH_WIKI : "" );
        String bookName = FriendlyUrlUtils.convertToFriendlyUrl( book.getCode( ) );
        String strPath = SLASH + wikiPath + bookName;

        FriendlyUrl url = createFriendlyUrl( strPath, strTechnicalUrl, currentRevision );
        list.add( url );
    }

    /**
     * Generate friendly URL for a wiki space
     */
    private void generateSpaceUrl( AbstractWikiItem space, List<FriendlyUrl> list, GeneratorOptions options )
    {
        Revision currentRevision = RevisionHome.getCurrentRevision( space.getId( ) );

        if ( currentRevision == null )
        {
            return;
        }

        String strTechnicalUrl = MessageFormat.format( TECHNICAL_URL_SPACE, space.getCode( ) );
        String wikiPath = ( options.isAddPath( ) ? PATH_WIKI : "" );
        String spaceName = FriendlyUrlUtils.convertToFriendlyUrl( space.getCode( ) );
        String strPath = SLASH + wikiPath + spaceName;

        FriendlyUrl url = createFriendlyUrl( strPath, strTechnicalUrl, currentRevision );
        list.add( url );
    }

    /**
     * Create a FriendlyUrl with common properties
     */
    private FriendlyUrl createFriendlyUrl( String strPath, String strTechnicalUrl, Revision revision )
    {
        FriendlyUrl url = new FriendlyUrl( );
        url.setFriendlyUrl( strPath );
        url.setTechnicalUrl( strTechnicalUrl );
        url.setCanonical( _bCanonical );
        url.setSitemap( _bSitemap );
        url.setSitemapChangeFreq( _strChangeFreq );
        url.setSitemapLastmod( revision != null ? SitemapUtils.formatDate( revision.getDateCreation( ) ) : "" );
        url.setSitemapPriority( _strPriority );
        return url;
    }

    /**
     * Find the parent book code by traversing up the item hierarchy
     */
    private String findParentBookCode( AbstractWikiItem item )
    {
        AbstractWikiItem current = item.getParent( );
        while ( current != null )
        {
            if ( current.getType( ) == WikiItemType.BOOK )
            {
                return current.getCode( );
            }
            current = current.getParent( );
        }
        return null;
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public String getName( )
    {
        return GENERATOR_NAME;
    }

    /**
     * Initialize generator with options stored with Datastore
     */
    private void init( )
    {
        String strKeyPrefix = SEODataKeys.PREFIX_GENERATOR + getClass( ).getName( );
        _bCanonical = DatastoreService.getDataValue( strKeyPrefix + SEODataKeys.SUFFIX_CANONICAL,
                DatastoreService.VALUE_TRUE ).equals( DatastoreService.VALUE_TRUE );
        _bSitemap = DatastoreService.getDataValue( strKeyPrefix + SEODataKeys.SUFFIX_SITEMAP,
                DatastoreService.VALUE_TRUE ).equals( DatastoreService.VALUE_TRUE );
        _strChangeFreq = DatastoreService.getDataValue( strKeyPrefix + SEODataKeys.SUFFIX_CHANGE_FREQ,
                DEFAULT_CHANGE_FREQ );
        _strPriority = DatastoreService.getDataValue( strKeyPrefix + SEODataKeys.SUFFIX_PRIORITY, DEFAULT_PRIORITY );
    }
}
