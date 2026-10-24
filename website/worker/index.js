// Serves dist/ and sends www.chronotif.com to the apex domain.
export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.hostname === "www.chronotif.com") {
      url.hostname = "chronotif.com";
      return Response.redirect(url.toString(), 301);
    }
    return env.ASSETS.fetch(request);
  },
};
