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
import fr.paris.lutece.plugins.wiki.business.Topic;
import fr.paris.lutece.plugins.wiki.business.TopicHome;
import fr.paris.lutece.plugins.wiki.business.TopicVersion;
import fr.paris.lutece.plugins.wiki.business.TopicVersionHome;
import fr.paris.lutece.plugins.wiki.business.WikiContent;
import fr.paris.lutece.plugins.wiki.service.WikiLocaleService;
import fr.paris.lutece.portal.service.datastore.DatastoreService;
import fr.paris.lutece.portal.service.util.AppLogService;
import java.util.ArrayList;

import java.util.Collection;
import java.util.List;
import org.apache.commons.lang.StringUtils;


/**
 * Document Alias Generator
 */
public class WikiFriendlyUrlGenerator implements FriendlyUrlGenerator
{
    private static final String GENERATOR_NAME = "Wiki Friendly URL Generator";
    private static final String TECHNICAL_URL = "/jsp/site/Portal.jsp?page=wiki&amp;view=page&amp;page_name=";
    private static final String LANGUAGE_ARG = "&amp;language=" ;
    private static final String SLASH = "/";
    private static final String PATH_WIKI = "wiki/";
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
        Collection<Topic> listTopics = TopicHome.getTopicsList( );

        List<String> listLanguages = WikiLocaleService.getLanguages( );
        String defaultLanguage = WikiLocaleService.getDefaultLanguage( ) ;
        init(  );

        for ( Topic t : listTopics )
        {
            
            // get the page title of the last version
            TopicVersion lastTopicVersion = TopicVersionHome.findLastVersion(t.getIdTopic());

            if( lastTopicVersion == null )
            {
                AppLogService.error( "SEO Wiki indexer : No data was found for topic #" + t.getIdTopic() + ". Data may be corrupted.");
            }
            else
            {
                    
                for ( String strLanguage : listLanguages ) 
                {
                    FriendlyUrl url = new FriendlyUrl(  );

                    WikiContent lastContent = lastTopicVersion.getWikiContent( strLanguage ) ;
                    String pageTitle = (!StringUtils.isBlank(lastContent.getPageTitle( ))?lastContent.getPageTitle( ):t.getPageName()) ;

                    String strPath = SLASH ;
                    if ( options.isAddPath(  ) )  strPath += PATH_WIKI ;
                    strPath += FriendlyUrlUtils.convertToFriendlyUrl( pageTitle ) ;
                    if ( !defaultLanguage.equals( strLanguage ) ) strPath += "." + strLanguage;
                    url.setFriendlyUrl( strPath );
                    

                    String strTechnicalUrl = TECHNICAL_URL + t.getPageName(  ) ;
                    if ( !defaultLanguage.equals( strLanguage ) ) strTechnicalUrl += LANGUAGE_ARG + strLanguage;
                    url.setTechnicalUrl( strTechnicalUrl );
                    
                    url.setCanonical( _bCanonical );
                    url.setSitemap( _bSitemap );
                    url.setSitemapChangeFreq( _strChangeFreq );

                    url.setSitemapLastmod( SitemapUtils.formatDate( lastTopicVersion.getDateEdition(  ) ) );

                    url.setSitemapPriority( _strPriority );
                    list.add( url );

                }
            }
        }

        return "";
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public String getName(  )
    {
        return GENERATOR_NAME;
    }

    /**
     * Initialiaze generator with options stored with Datastore
     */
    private void init(  )
    {
        String strKeyPrefix = SEODataKeys.PREFIX_GENERATOR + getClass(  ).getName(  );
        _bCanonical = DatastoreService.getDataValue( strKeyPrefix + SEODataKeys.SUFFIX_CANONICAL,
                DatastoreService.VALUE_TRUE ).equals( DatastoreService.VALUE_TRUE );
        _bSitemap = DatastoreService.getDataValue( strKeyPrefix + SEODataKeys.SUFFIX_SITEMAP,
                DatastoreService.VALUE_TRUE ).equals( DatastoreService.VALUE_TRUE );
        _strChangeFreq = DatastoreService.getDataValue( strKeyPrefix + SEODataKeys.SUFFIX_CHANGE_FREQ,
                DEFAULT_CHANGE_FREQ );
        _strPriority = DatastoreService.getDataValue( strKeyPrefix + SEODataKeys.SUFFIX_PRIORITY, DEFAULT_PRIORITY );
    }
}
