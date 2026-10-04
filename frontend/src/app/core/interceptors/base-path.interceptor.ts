import { HttpInterceptorFn } from '@angular/common/http';

/**
 * Routes API requests to the correct backend service based on the endpoint path.
 * 
 * - Auth endpoints (/auth/*) → http://localhost:3000 (auth-service)
 * - Trade endpoints (/api/v1/*, /api/dashboard, /api/portfolio-allocation, /api/market-overview, /api/top-movers) → http://localhost:8083 (trade-api)
 */
export const basePathInterceptor: HttpInterceptorFn = (req, next) => {
  // Only modify relative URLs or localhost URLs
  if (req.url.startsWith('/') || req.url.includes('localhost')) {
    let modifiedUrl = req.url;

    // If it's a relative URL, construct the full URL with the appropriate base path
    if (req.url.startsWith('/')) {
      if (req.url.startsWith('/auth')) {
        // Auth endpoints go to auth-service on port 3000
        modifiedUrl = `http://localhost:3000${req.url}`;
      } else if (req.url.startsWith('/api/v1') || req.url.startsWith('/api/dashboard') || req.url.startsWith('/api/portfolio-allocation') || req.url.startsWith('/api/market-overview') || req.url.startsWith('/api/top-movers')) {
        // Trade endpoints go to trade-api on port 8083
        modifiedUrl = `http://localhost:8083${req.url}`;
      }
    } else if (req.url.startsWith('http://localhost:3000')) {
      // Already routed to auth-service, keep as is
      modifiedUrl = req.url;
    } else if (req.url.startsWith('http://localhost:8083')) {
      // Already routed to trade-api, keep as is
      modifiedUrl = req.url;
    } else {
      // For other localhost URLs, parse and route appropriately
      const urlObj = new URL(req.url);
      if (urlObj.pathname.startsWith('/auth')) {
        modifiedUrl = `http://localhost:3000${urlObj.pathname}${urlObj.search}`;
      } else if (urlObj.pathname.startsWith('/api/v1') || urlObj.pathname.startsWith('/api/dashboard') || urlObj.pathname.startsWith('/api/portfolio-allocation') || urlObj.pathname.startsWith('/api/market-overview') || urlObj.pathname.startsWith('/api/top-movers')) {
        modifiedUrl = `http://localhost:8083${urlObj.pathname}${urlObj.search}`;
      }
    }

    if (modifiedUrl !== req.url) {
      req = req.clone({ url: modifiedUrl });
    }
  }

  return next(req);
};
