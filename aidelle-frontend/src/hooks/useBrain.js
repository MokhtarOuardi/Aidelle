
const agentApiUrl = import.meta.env.VITE_AGENT_API_URL;

export const useBrain = () => {
  const getResponse = async (userInput) => {
    if (!agentApiUrl) {
      console.error('Agent API URL is missing. Please set VITE_AGENT_API_URL in your .env file.');
      return "I'm sorry, my brain isn't connected yet. Please check the API configuration.";
    }

    try {
      const response = await fetch(`${agentApiUrl}/chat`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ message: userInput }),
      });

      if (!response.ok) {
        throw new Error(`Agent API responded with status: ${response.status}`);
      }

      const data = await response.json();
      let finalContent = data.response;
      
      // 1. Flatten the response if it's a rich-text object or array
      if (Array.isArray(finalContent)) {
          finalContent = finalContent.map(part => part.text || part).join(' ');
      } else if (typeof finalContent === 'object' && finalContent !== null) {
          finalContent = finalContent.text || JSON.stringify(finalContent);
      }
      
      // 2. Strip Markdown and Special Characters (for TTS and Subtitles)
      // Remove Bold/Italic asterisks/underscores, bullets, and headings
      finalContent = finalContent
          .replace(/[#*`_~]/g, '') // Remove formatting chars: #, *, `, _, ~
          .replace(/\[(.*?)\]\(.*?\)/g, '$1') // Standardize links to [text]
          .replace(/^[ \t]*[-+*][ \t]+/gm, '') // Remove bullet points at start of lines
          .trim();

      return finalContent || "I didn't quite catch that. Could you say it again?";
    } catch (error) {
      console.error('Agent API Error:', error);
      return "I'm having a bit of trouble thinking right now. Let's try again in a moment.";
    }
  };

  return { getResponse };
};
