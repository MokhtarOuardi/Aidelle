import { Groq } from 'groq-sdk';

const apiKey = import.meta.env.VITE_GROQ_API_KEY;

const groq = new Groq({
  apiKey: apiKey,
  dangerouslyAllowBrowser: true,
});

export const useBrain = () => {
  const getResponse = async (userInput) => {
    if (!apiKey) {
      console.error('Groq API Key is missing. Please set VITE_GROQ_API_KEY in your .env file.');
      return "I'm sorry, my brain isn't connected yet. Please check the API key.";
    }

    try {
      const completion = await groq.chat.completions.create({
        messages: [
          {
            role: 'system',
            content: 'You are a kind, patient assistant for the elderly. Keep responses short (under 2 sentences) and clear.',
          },
          {
            role: 'user',
            content: userInput,
          },
        ],
        model: 'llama-3.1-8b-instant',
      });

      return completion.choices[0]?.message?.content || "I didn't quite catch that. Could you say it again?";
    } catch (error) {
      console.error('Groq Error:', error);
      return "I'm having a bit of trouble thinking right now. Let's try again in a moment.";
    }
  };

  return { getResponse };
};
